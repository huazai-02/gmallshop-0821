package com.atguigu.gmall.pms.feign;

import com.atguigu.gmall.sms.api.controller.GmallSmsApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient("sms-service")
public interface GmallSmsClient extends GmallSmsApi {
}
