package com.gmall.manageweb.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.gmall.bean.SkuInfo;
import com.gmall.service.ManageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SkuController {

    @Reference
    ManageService manageService;

    @PostMapping("saveSkuInfo")
    public String  saveSkuInfo(@RequestBody SkuInfo skuInfo){
        manageService.saveSkuInfo(skuInfo);
        return  "success";
    }

}