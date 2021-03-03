package com.atguigu.gmall.wms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.wms.mapper.WareSkuMapper;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.service.WareSkuService;
import org.springframework.transaction.annotation.Transactional;


@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuMapper, WareSkuEntity> implements WareSkuService {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private WareSkuMapper wareSkuMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;


    private static final String LOCAL_PREFIX="stock:lock:";
    private static final String KEY_PREFIX="stock:info:";



    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<WareSkuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<WareSkuEntity>()
        );

        return new PageResultVo(page);
    }

    // 提交订单时，要进行验证库存是否有货，并且是否要锁定(要保证原子性；引入分布式锁)
    @Override
    @Transactional
    public List<SkuLockVo> checkAndLock(List<SkuLockVo> lockVos,String OrderToken) {
        if (CollectionUtils.isEmpty(lockVos)){
            throw new OrderException("您没有要购买的商品");
        }

        //A. 遍历所有商品，验库存并锁库存，要具备原子性
        lockVos.forEach(lockVo -> {
            this.checkLock(lockVo);
        });

        //B. 只要有一个商品锁定失败，所有已经锁成功的都要解锁
        if (lockVos.stream().anyMatch(lockVo -> !lockVo.getLock())) {
            // 获取所有锁定成功的商品，遍历解锁库存
            lockVos.stream().filter(SkuLockVo::getLock).forEach(lockVo->{
                this.wareSkuMapper.unLock(lockVo.getWareSkuId(),lockVo.getCount());
            });
            // 相应锁定状态
            return lockVos;
        }

        // 如果所有商品都锁定成功的情况下，需要缓存锁定信息到redis。以便将来解锁库存或减库存
        // （因为所有人都在操作，如果不将锁定的仓库信息存起来，将来都不知道谁的订单解锁的；也不知道自己锁定的是哪个仓库。）
        // 以 OrderToken 为key, lockvos为value
        this.redisTemplate.opsForValue().set(KEY_PREFIX+OrderToken, JSON.toJSONString(lockVos));

        // 锁定库存成功后，定时解锁库存
        this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE", "stock.ttl",OrderToken);
        return null;
    }




    // 为了保证验库存和锁库存的原子性。定义一个分布式锁方法，在其内部进行验库和锁库。
    private void checkLock(SkuLockVo lockVo){
        RLock fairLock = this.redissonClient.getFairLock(LOCAL_PREFIX+lockVo.getSkuId() );// 加skuid保证只锁当前库存对象
        fairLock.lock();

        try {
            // 验库存：查询
            List<WareSkuEntity> wareSkuEntityList = this.wareSkuMapper.check(lockVo.getSkuId(), lockVo.getCount());
            // 判断是否有符合要求的库存，没有就验库存失败
            if (CollectionUtils.isEmpty(wareSkuEntityList)){
                lockVo.setLock(false);
                return;
            }
            // 大数据分析，获取就近的参数，这里取第一个
            WareSkuEntity wareSkuEntity = wareSkuEntityList.get(0);

            // 锁库存：更新
            Long wareSkuId = wareSkuEntity.getId();
            if (this.wareSkuMapper.lock(wareSkuId,lockVo.getCount()) == 1){
                lockVo.setLock(true);
                lockVo.setWareSkuId(wareSkuId);//如果库存锁定成功，要记录下锁定的仓库ID，以方便将来解锁库存
            }
        } finally {
            fairLock.unlock();
        }
    }
}