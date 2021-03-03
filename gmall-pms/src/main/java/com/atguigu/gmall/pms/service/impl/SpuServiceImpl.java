package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.feign.GmallSmsClient;
import com.atguigu.gmall.pms.mapper.*;
import com.atguigu.gmall.pms.service.*;
import com.atguigu.gmall.pms.vo.SkuVo;
import com.atguigu.gmall.pms.vo.SpuAttrValueVo;
import com.atguigu.gmall.pms.vo.SpuVo;
import com.atguigu.gmall.sms.api.vo.SkuSaleVo;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import javax.annotation.Resource;


@Service("spuService")
public class SpuServiceImpl extends ServiceImpl<SpuMapper, SpuEntity> implements SpuService {

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SpuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public PageResultVo getSpuInfo(long categoryId, PageParamVo paramVo) {

        QueryWrapper<SpuEntity> queryWrapper = new QueryWrapper<>();
        if (categoryId != 0) {
            queryWrapper.eq("category_id", categoryId);
        }
        //关键字查询
        String key = paramVo.getKey();
        if (StringUtils.isNotBlank(key)) {
            queryWrapper.and(t -> t.eq("id", key).or().like("name", key));
        }

        IPage<SpuEntity> page = this.page(paramVo.getPage(), queryWrapper);
        return new PageResultVo(page);

    }

    @Resource
    private SpuDescMapper spuDescMapper;

    @Autowired
    private SpuAttrValueService spuAttrValueService;

    @Resource
    SkuMapper skuMapper;

    @Resource
    SkuImagesService skuImagesService;

    @Resource
    SkuAttrValueService skuAttrValueService;

    @Autowired
    GmallSmsClient gmallSmsClient;

    @Autowired
    SpuDescService spuDescService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @GlobalTransactional
    @Override
    public void bigSave(SpuVo spuVo) {

        //1.首先保存spu表格数据

        //1.1 保存spu表格
        Long spuId = saveSpu(spuVo);

        //1.2 保存spu_dec表格

//        saveSpuDesc(spuVo, spuId);
        this.spuDescService.saveSpuDesc(spuVo,spuId);
//        int i=1/0;

        //1.3 保存spu_attr_value表
        saveBaseAttr(spuVo, spuId);


        //2.再保存sku表格数据
        List<SkuVo> skuList = spuVo.getSkus();
        if (CollectionUtils.isEmpty(skuList)){
            return;
        }

        skuList.forEach(sku -> {
            //2.1 保存sku表格
            sku.setSpuId(spuId);
            sku.setCategoryId(spuVo.getCategoryId());
            sku.setBrandId(spuVo.getBrandId());
            List<String> images = sku.getImages();
            if (!CollectionUtils.isEmpty(images)){
                sku.setDefaultImage(StringUtils.isNotEmpty(sku.getDefaultImage())?sku.getDefaultImage():images.get(0));
            }
            skuMapper.insert(sku);
            Long skuId = sku.getId();

            //2.2 保存sku_images表
            if (!CollectionUtils.isEmpty(images)){
                skuImagesService.saveBatch(images.stream().map(image->{
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setUrl(image);
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setDefaultStatus(StringUtils.equals(sku.getDefaultImage(),image)?1:0);
                    return skuImagesEntity;
                }).collect(Collectors.toList()));
            }

            //2.3 保存sku_attr_value表
            List<SkuAttrValueEntity> saleAttrs = sku.getSaleAttrs();
            if (!CollectionUtils.isEmpty(saleAttrs)){
                saleAttrs.forEach(skuAttrValueEntity->{skuAttrValueEntity.setSkuId(skuId);});
                skuAttrValueService.saveBatch(saleAttrs);
            }

            //3.最后保存营销的数据表格
            SkuSaleVo skuSaleVo = new SkuSaleVo();
            BeanUtils.copyProperties(sku, skuSaleVo);
            skuSaleVo.setSkuId(skuId);
            gmallSmsClient.saveSales(skuSaleVo);

        });
        this.rabbitTemplate.convertAndSend("PMS_ITEM_EXCHANGE", "item.update", spuId);
    }

    private void saveBaseAttr(SpuVo spuVo, Long spuId) {
        List<SpuAttrValueVo> baseAttrs = spuVo.getBaseAttrs();
        if (CollectionUtils.isNotEmpty(baseAttrs)){
            //利用lambda表达式的map集合之间转换的功能进行转换。
            spuAttrValueService.saveBatch(baseAttrs.stream().map(spuAttrValueVo->{
                SpuAttrValueEntity spuAttrValueEntity = new SpuAttrValueEntity();
                BeanUtils.copyProperties(spuAttrValueVo, spuAttrValueEntity);
                spuAttrValueEntity.setSpuId(spuId);
                return spuAttrValueEntity;
            }).collect(Collectors.toList()));
        }
    }

//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public void saveSpuDesc(SpuVo spuVo, Long spuId) {
//        List<String> spuImages = spuVo.getSpuImages();
//        if (CollectionUtils.isNotEmpty(spuImages)){
//            SpuDescEntity spuDescEntity = new SpuDescEntity();
//            spuDescEntity.setSpuId(spuId);
//            spuDescEntity.setDecript(StringUtils.join(spuImages,","));
//            spuDescMapper.insert(spuDescEntity);
//        }
//    }

    private Long saveSpu(SpuVo spuVo) {
        spuVo.setPublishStatus(1);//默认都是上架
        spuVo.setCreateTime(new Date());
        spuVo.setUpdateTime(spuVo.getCreateTime());
        this.save(spuVo);
        return spuVo.getId();
    }







//    public static void main(String[] args) {
//        List<User> users = Arrays.asList(new User(11, "马云", 10),
//                new User(21, "马化腾", 20),
//                new User(31, "马克思", 30));
//        // 过滤filter()    集合之间的转化Map     求和reduce
//        users.stream().filter(user -> user.getAge()>20).collect(Collectors.toList()).forEach(System.out::println);
//
//        users.stream().map(user -> user.getName()).collect(Collectors.toList()).forEach(System.out::println);
//
//        // 要将List<User> 转化为List<person> 实质上就是利用map()，将集合中的每个User对象转化为person即可。
//        users.stream().map(user -> {
//            Person person = new Person();
//            person.setId(user.getId());
//            person.setAge(user.getAge());
//            person.setUserName(user.getName());
//            return person;
//        }).collect(Collectors.toList()).forEach(System.out::println);
//
//        // 使用reduce 求集合中的某中元素之和
//        System.out.println(users.stream().map(User::getAge).reduce((a, b) -> a + b).get());
//    }

}

//@Data
//@AllArgsConstructor
//@NoArgsConstructor
//class User{
//
//    private Integer id;
//    private String name;
//    private Integer age;
//}
//
//@Data
//@NoArgsConstructor
//@AllArgsConstructor
//class Person{
//    private Integer id;
//    private String userName;
//    private Integer age;
//}