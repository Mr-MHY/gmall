package com.gmall.orderweb.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.gmall.bean.*;
import com.gmall.service.CartService;
import com.gmall.service.ManageService;
import com.gmall.service.OrderService;
import com.gmall.service.UserService;
import com.gmall.web.config.LoginRequire;
import com.gmall.web.util.HttpClientUtil;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Controller
public class OrderController {

    @Reference
    UserService userService;

    @Reference
    CartService cartService;

    @Reference
    OrderService orderService;

    @Reference
    ManageService manageService;

    /**
     * 交易订单，去结算
     * 获得用户地址
     * 获得用户需要结算的订单以及总数量
     * 生成并跳转到订单
     *
     * @param request
     * @return
     */
    @GetMapping("trade")
    @LoginRequire
    public String trade(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        //  用户地址  列表
        List<UserAddress> userAddressList = userService.getUserAddressList(userId);

        request.setAttribute("userAddressList", userAddressList);

        //  用户需要结账的商品清单
        List<CartInfo> checkedCartList = cartService.getCheckedCartList(userId);
        BigDecimal totalAmount = new BigDecimal("0");
        for (CartInfo cartInfo : checkedCartList) {
            BigDecimal cartInfoAmount = cartInfo.getSkuPrice().multiply(new BigDecimal(cartInfo.getSkuNum()));
            totalAmount = totalAmount.add(cartInfoAmount);
        }

        String token = orderService.genToken(userId);

        request.setAttribute("tradeNo", token);

        request.setAttribute("checkedCartList", checkedCartList);

        request.setAttribute("totalAmount", totalAmount);

        return "trade";
    }

    /**
     * 提交订单
     * 获取userID,交易号
     * 判断是否已失效
     * 设置订单状态
     * 补全订单项内容
     * 保存订单
     * 跳转到支付
     *
     * @param orderInfo
     * @param request
     * @return
     */
    @PostMapping("submitOrder")
    @LoginRequire
    public String submitOrder(OrderInfo orderInfo, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");

        String tradeNo = request.getParameter("tradeNo");

        boolean isEnableToken = orderService.verifyToken(userId, tradeNo);
        if (!isEnableToken) {
            request.setAttribute("errMsg", "页面已失效，请重新结算！");
            return "tradeFail";
        }

        orderInfo.setOrderStatus(OrderStatus.UNPAID);
        orderInfo.setProcessStatus(ProcessStatus.UNPAID);
        orderInfo.setCreateTime(new Date());
        orderInfo.setExpireTime(DateUtils.addMinutes(new Date(), 15));
        orderInfo.sumTotalAmount();
        orderInfo.setUserId(userId);

        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            SkuInfo skuInfo = manageService.getSkuInfo(orderDetail.getSkuId());
            orderDetail.setImgUrl(skuInfo.getSkuDefaultImg());
            orderDetail.setSkuName(skuInfo.getSkuName());

            if (!orderDetail.getOrderPrice().equals(skuInfo.getPrice())) {
                request.setAttribute("errMsg", "商品价格发生变动，请重新下单");
                return "tradeFail";
            }
        }
        List<OrderDetail> errList = Collections.synchronizedList(new ArrayList<>());
        Stream<CompletableFuture<String>> completableFutureStream = orderDetailList.stream().map(orderDetail ->
                CompletableFuture.supplyAsync(() -> checkSkuNum(orderDetail)).whenComplete((hasStock, ex) -> {
                    if (hasStock.equals("0")) {
                        errList.add(orderDetail);
                    }
                })
        );
        CompletableFuture[] completableFutures = completableFutureStream.toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(completableFutures).join();
        if (errList.size() > 0) {
            StringBuffer errStringBuffer = new StringBuffer();
            for (OrderDetail orderDetail : errList) {
                errStringBuffer.append("商品：" + orderDetail.getSkuName() + "库存暂时不足！");
            }
            request.setAttribute("errMsg", errStringBuffer.toString());
            return "tradeFail";
        }
        String orderId = orderService.saveOrder(orderInfo);

        return "redirect://payment.gmall.com/index?orderId=" + orderId;
    }

    public String checkSkuNum(OrderDetail orderDetail) {
        String hasStock = HttpClientUtil.doGet("http://www.gware.com/hasStock?skuId=" + orderDetail.getSkuId() + "&num=" + orderDetail.getSkuNum());
        return hasStock;
    }


    //   List  1,2,3,4,5,6,7,8,9    找出 所有能够被3整除的数  放到一个清单里
    @Test
    public void test1() {
        List<Integer> list = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9);
        // List  rsList=new CopyOnWriteArrayList();   适合多读少写
        List rsList = Collections.synchronizedList(new ArrayList<>());  //适合多写少读
        Stream<CompletableFuture<Boolean>> completableFutureStream = list.stream().map(num ->
                CompletableFuture.supplyAsync(() -> checkNum(num)).whenComplete((ifPass, ex) -> {
                    //supplyAsync中 添加异步执行的线程处理任务  //whenComplete 添加线程执行完毕后的造操作  //
                    if (ifPass) {
                        rsList.add(num);
                    }
                })
        );   // 流式处理 相当于把list<integer>里的转化为一个  Future数组  ,Future可以理解为一个不知道什么时候执行完的异步结果
        CompletableFuture[] completableFutures = completableFutureStream.toArray(CompletableFuture[]::new);

        // 归集操作allOf代表此处阻塞 直到线程全部执行完   anyOf代表阻塞到只要有一个执行完就可。
        CompletableFuture.allOf(completableFutures).join();

        System.out.println(rsList);
    }

    private Boolean checkNum(Integer num) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (num % 3 == 0) {
            return true;
        } else {
            return false;
        }

    }
}