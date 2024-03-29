package com.gmall.manageservice.service.impl;

import com.alibaba.dubbo.config.annotation.Service;

import com.alibaba.fastjson.JSON;
import com.gmall.bean.*;
import com.gmall.manageservice.mapper.*;
import com.gmall.service.ManageService;
import com.gmall.util.RedisUtil;
import org.apache.commons.lang3.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import redis.clients.jedis.Jedis;
import tk.mybatis.mapper.entity.Example;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Service
public class ManageServiceImpl implements ManageService {

    @Autowired
    RedisUtil redisUtil;
    @Autowired
    SkuAttrValueMapper skuAttrValueMapper;
    @Autowired
    SkuImageMapper skuImageMapper;
    @Autowired
    SkuInfoMapper skuInfoMapper;
    @Autowired
    SkuSaleAttrValueMapper skuSaleAttrValueMapper;

    @Autowired
    SpuImageMapper spuImageMapper;
    @Autowired
    SpuInfoMapper spuInfoMapper;
    @Autowired
    SpuSaleAttrMapper spuSaleAttrMapper;
    @Autowired
    SpuSaleAttrValueMapper spuSaleAttrValueMapper;

    @Autowired
    BaseSaleAttrMapper baseSaleAttrMapper;

    @Autowired
    BaseAttrValueMapper baseAttrValueMapper;

    @Autowired
    BaseAttrInfoMapper baseAttrInfoMapper;

    @Autowired
    BaseCatalog1Mapper baseCatalog1Mapper;

    @Autowired
    BaseCatalog2Mapper baseCatalog2Mapper;

    @Autowired
    BaseCatalog3Mapper baseCatalog3Mapper;

    public static final String SKUKEY_PREFIX = "sku:";
    public static final String SKUKEY_INFO_SUFFIX = ":info";
    public static final String SKUKEY_LOCK_SUFFIX = ":lock";

    @Override
    public List<BaseCatalog1> getCatalog1() {
        return baseCatalog1Mapper.selectAll();
    }

    @Override
    public List<BaseCatalog2> getCatalog2(String catalog1Id) {
        BaseCatalog2 baseCatalog2 = new BaseCatalog2();
        baseCatalog2.setCatalog1Id(catalog1Id);
        List<BaseCatalog2> baseCatalog2List = baseCatalog2Mapper.select(baseCatalog2);
        return baseCatalog2List;
    }

    @Override
    public List<BaseCatalog3> getCatalog3(String catalog2Id) {
        BaseCatalog3 baseCatalog3 = new BaseCatalog3();
        baseCatalog3.setCatalog2Id(catalog2Id);
        List<BaseCatalog3> baseCatalog3List = baseCatalog3Mapper.select(baseCatalog3);
        return baseCatalog3List;
    }

    @Override
    public List<BaseAttrInfo> getAttrList(String catalog3Id) {

//        Example example = new Example(BaseAttrInfo.class);
//        example.createCriteria().andEqualTo("catalog3Id",catalog3Id);
//
//        List<BaseAttrInfo> baseAttrInfoList = baseAttrInfoMapper.selectByExample(example);
//        for (BaseAttrInfo baseAttrInfo : baseAttrInfoList) {
//            BaseAttrValue baseAttrValue = new BaseAttrValue();
//            baseAttrValue.setAttrId(baseAttrInfo.getId());
//            List<BaseAttrValue> baseAttrValueList = baseAttrValueMapper.select(baseAttrValue);
//            baseAttrInfo.setAttrValueList(baseAttrValueList);
//        }
//        return baseAttrInfoList;
        List<BaseAttrInfo> baseAttrList = baseAttrInfoMapper.getBaseAttrInfoListByCatalog3Id(catalog3Id);
        return baseAttrList;
    }

    @Override
    public BaseAttrInfo getBaseAttrInfo(String attrId) {
        BaseAttrInfo baseAttrInfo = baseAttrInfoMapper.selectByPrimaryKey(attrId);

        BaseAttrValue baseAttrValueQuery = new BaseAttrValue();
        baseAttrValueQuery.setAttrId(attrId);
        List<BaseAttrValue> baseAttrValueList = baseAttrValueMapper.select(baseAttrValueQuery);

        baseAttrInfo.setAttrValueList(baseAttrValueList);

        return baseAttrInfo;
    }

    @Override
    @Transactional
    public void saveAttrInfo(BaseAttrInfo baseAttrInfo) {
        if (baseAttrInfo.getId() != null && baseAttrInfo.getId().length() > 0) {
            baseAttrInfoMapper.updateByPrimaryKeySelective(baseAttrInfo);
        } else {
            baseAttrInfo.setId(null);
            baseAttrInfoMapper.insertSelective(baseAttrInfo);
        }
        Example example = new Example(BaseAttrValue.class);
        example.createCriteria().andEqualTo("attrId", baseAttrInfo.getId());
        //根据attrid先全部删除，再统一保存
        baseAttrValueMapper.deleteByExample(example);

        List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
        for (BaseAttrValue baseAttrValue : attrValueList) {
            String id = baseAttrInfo.getId();
            baseAttrValue.setAttrId(id);
            baseAttrValueMapper.insertSelective(baseAttrValue);
        }


    }

    @Override
    public List<BaseSaleAttr> getBaseSaleAttrList() {
        return baseSaleAttrMapper.selectAll();
    }

    @Override
    public void saveSpuInfo(SpuInfo spuInfo) {
        //spu基本信息
        spuInfoMapper.insertSelective(spuInfo);
        // 图片信息
        List<SpuImage> spuImageList = spuInfo.getSpuImageList();
        for (SpuImage spuImage : spuImageList) {
            spuImage.setSpuId(spuInfo.getId());
            spuImageMapper.insertSelective(spuImage);
        }

        // 销售属性
        List<SpuSaleAttr> spuSaleAttrList = spuInfo.getSpuSaleAttrList();
        for (SpuSaleAttr spuSaleAttr : spuSaleAttrList) {
            spuSaleAttr.setSpuId(spuInfo.getId());
            spuSaleAttrMapper.insertSelective(spuSaleAttr);

            // 销售属性值
            List<SpuSaleAttrValue> spuSaleAttrValueList = spuSaleAttr.getSpuSaleAttrValueList();
            for (SpuSaleAttrValue spuSaleAttrValue : spuSaleAttrValueList) {
                spuSaleAttrValue.setSpuId(spuInfo.getId());
                spuSaleAttrValueMapper.insertSelective(spuSaleAttrValue);
            }

        }

    }

    @Override
    public List<SpuInfo> getSpuList(String catalog3Id) {
        SpuInfo spuInfo = new SpuInfo();
        spuInfo.setCatalog3Id(catalog3Id);
        return spuInfoMapper.select(spuInfo);
    }

    @Override
    public List<SpuImage> getSpuImageList(String spuId) {
        SpuImage spuImage = new SpuImage();
        spuImage.setSpuId(spuId);
        return spuImageMapper.select(spuImage);
    }

    @Override
    public List<SpuSaleAttr> getSpuSaleAttrList(String spuId) {
        return spuSaleAttrMapper.getSpuSaleAttrListBySpuId(spuId);
    }

    @Override
    @Transactional
    public void saveSkuInfo(SkuInfo skuInfo) {
        if (skuInfo.getId() == null || skuInfo.getId().length() == 0) {
            skuInfoMapper.insertSelective(skuInfo);
        } else {
            skuInfoMapper.updateByPrimaryKeySelective(skuInfo);
        }

        SkuAttrValue skuAttrValue = new SkuAttrValue();
        skuAttrValue.setSkuId(skuInfo.getId());
        skuAttrValueMapper.delete(skuAttrValue);

        List<SkuAttrValue> skuAttrValueList = skuInfo.getSkuAttrValueList();
        for (SkuAttrValue attrValue : skuAttrValueList) {
            attrValue.setSkuId(skuInfo.getId());
            skuAttrValueMapper.insertSelective(attrValue);
        }

        SkuSaleAttrValue skuSaleAttrValue = new SkuSaleAttrValue();
        skuSaleAttrValue.setSkuId(skuInfo.getId());
        skuSaleAttrValueMapper.delete(skuSaleAttrValue);
        List<SkuSaleAttrValue> skuSaleAttrValueList = skuInfo.getSkuSaleAttrValueList();
        for (SkuSaleAttrValue saleAttrValue : skuSaleAttrValueList) {
            saleAttrValue.setSkuId(skuInfo.getId());
            skuSaleAttrValueMapper.insertSelective(saleAttrValue);
        }
        SkuImage skuImage4Del = new SkuImage();
        skuImage4Del.setSkuId(skuInfo.getId());
        skuImageMapper.delete(skuImage4Del);
        List<SkuImage> skuImageList = skuInfo.getSkuImageList();
        for (SkuImage image : skuImageList) {
            image.setSkuId(skuInfo.getId());
            skuImageMapper.insert(image);
        }
    }

    public SkuInfo getSkuInfoDB(String skuId) {
        System.err.println(Thread.currentThread() + "读取数据库");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        SkuInfo skuInfo = skuInfoMapper.selectByPrimaryKey(skuId);
        if (skuInfo == null) {
            return null;
        }
        SkuImage skuImage = new SkuImage();
        skuImage.setSkuId(skuId);
        List<SkuImage> skuImageList = skuImageMapper.select(skuImage);
        skuInfo.setSkuImageList(skuImageList);

        SkuSaleAttrValue skuSaleAttrValue = new SkuSaleAttrValue();
        skuSaleAttrValue.setSkuId(skuId);
        List<SkuSaleAttrValue> skuSaleAttrValueList = skuSaleAttrValueMapper.select(skuSaleAttrValue);
        skuInfo.setSkuSaleAttrValueList(skuSaleAttrValueList);

        SkuAttrValue skuAttrValue = new SkuAttrValue();
        skuAttrValue.setSkuId(skuId);
        List<SkuAttrValue> skuAttrValueList = skuAttrValueMapper.select(skuAttrValue);
        skuInfo.setSkuAttrValueList(skuAttrValueList);
        return skuInfo;
    }

    public SkuInfo getSkuInfo_redis(String skuId) {
        SkuInfo skuInfoResult = null;
        Jedis jedis = redisUtil.getJedis();
        int SKU_EXPIRE_SEC = 100;
        String skuKey = SKUKEY_PREFIX + skuId + SKUKEY_INFO_SUFFIX;
        String skuInfoJson = jedis.get(skuKey);
        if (skuInfoJson != null) {
            if (!"EMPTY".equals(skuInfoJson)) {
                System.out.println(Thread.currentThread() + "命中缓存");
                skuInfoResult = JSON.parseObject(skuInfoJson, SkuInfo.class);
            }
        } else {
            System.out.println(Thread.currentThread() + "未命中缓存");
            String lockKey = SKUKEY_PREFIX + skuId + SKUKEY_LOCK_SUFFIX;
            String token = UUID.randomUUID().toString();
            String locked = jedis.set(lockKey, token, "NX", "EX", 100);
            if ("OK".equals(locked)) {
                System.out.println(Thread.currentThread() + "得到锁");
                skuInfoResult = getSkuInfoDB(skuId);
                System.out.println(Thread.currentThread() + "写入缓存");
                String skuInfoJsonResult = null;
                if (skuInfoResult != null) {
                    skuInfoJsonResult = JSON.toJSONString(skuInfoResult);
                } else {
                    skuInfoJsonResult = "EMPTY";
                }
                jedis.setex(skuKey, SKU_EXPIRE_SEC, skuInfoJsonResult);
                System.out.println(Thread.currentThread() + "释放锁");
                if (jedis.exists(lockKey) && token.equals(jedis.get(lockKey))) {
                    jedis.del(locked);
                }
            } else {
                System.out.println(Thread.currentThread() + "未得到锁，开始自旋等待");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                getSkuInfo(skuId);
            }
        }
        jedis.close();
        return skuInfoResult;
    }

    @Override
    public SkuInfo getSkuInfo(String skuId) {
        SkuInfo skuInfoResult = null;
        Jedis jedis = redisUtil.getJedis();
        int SKU_EXPIRE_SEC = 100;
        String skuKey = SKUKEY_PREFIX + skuId + SKUKEY_INFO_SUFFIX;
        String skuInfoJson = jedis.get(skuKey);
        if (skuInfoJson != null) {
            if (!"EMPTY".equals(skuInfoJson)) {
                System.out.println(Thread.currentThread() + "命中缓存");
                skuInfoResult = JSON.parseObject(skuInfoJson, SkuInfo.class);
            }
        } else {
            Config config = new Config();
            config.useSingleServer().setAddress("redis://192.168.220.130:6379");
            RedissonClient redissonClient = Redisson.create(config);
            String lockKey = SKUKEY_PREFIX + skuId + SKUKEY_LOCK_SUFFIX;
            RLock lock = redissonClient.getLock(lockKey);
            boolean locked = false;
            try {
                locked = lock.tryLock(10, 5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (locked) {
                System.out.println(Thread.currentThread() + "得到锁");
                System.out.println(Thread.currentThread() + "再次查询缓存");
                String skuInfoJsonResult = jedis.get(skuKey);
                if (skuInfoJsonResult != null) {
                    if (!"EMPTY".equals(skuInfoJsonResult)) {
                        System.out.println(Thread.currentThread() + "命中缓存");
                        JSON.parseObject(skuInfoJsonResult,SkuInfo.class);
                    }
                }else{
                    skuInfoResult = getSkuInfoDB(skuId);
                    System.out.println(Thread.currentThread()+"写入缓存");
                    if (skuInfoResult!=null){
                        skuInfoJsonResult=JSON.toJSONString(skuInfoResult);
                    }else{
                        skuInfoJsonResult="EMPTY";
                    }
                    jedis.setex(skuKey,SKU_EXPIRE_SEC,skuInfoJsonResult);
                }
                lock.unlock();
            }
        }
        return skuInfoResult;
//        SkuInfo skuInfo = skuInfoMapper.selectByPrimaryKey(skuId);
//
//        SkuImage skuImage = new SkuImage();
//        skuImage.setSkuId(skuId);
//        List<SkuImage> skuImageList = skuImageMapper.select(skuImage);
//        skuInfo.setSkuImageList(skuImageList);
//
//        SkuSaleAttrValue skuSaleAttrValue = new SkuSaleAttrValue();
//        skuSaleAttrValue.setSkuId(skuId);
//        List<SkuSaleAttrValue> skuSaleAttrValueList = skuSaleAttrValueMapper.select(skuSaleAttrValue);
//        skuInfo.setSkuSaleAttrValueList(skuSaleAttrValueList);
//
//        return skuInfo;
    }

    @Override
    public List<SpuSaleAttr> getSpuSaleAttrListCheckSku(String skuId, String spuId) {
        List<SpuSaleAttr> spuSaleAttrList = spuSaleAttrMapper.getSpuSaleAttrListBySpuIdCheckSku(skuId, spuId);
        return spuSaleAttrList;
    }

    @Override
    public Map getSkuValueIdsMap(String spuId) {
        List<Map> mapList = skuSaleAttrValueMapper.getSaleAttrValuesBySpu(spuId);
        Map skuValueIdsMap = new HashMap();

        for (Map map : mapList) {
            String skuId = (Long) map.get("sku_id") + "";
            String valueIds = (String) map.get("value_ids");
            skuValueIdsMap.put(valueIds, skuId);

        }
        return skuValueIdsMap;
    }

    @Override
    public List<BaseAttrInfo> getAttrList(List attrValueIdList) {
        //attrValueIdList -->  13,15, 54
        String valueIds = StringUtils.join(attrValueIdList.toArray(), ",");

        return  baseAttrInfoMapper.getBaseAttrInfoListByValueIds(valueIds);


    }
}
