package com.gmall.order.consumer;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.gmall.bean.OrderDetail;
import com.gmall.bean.OrderInfo;
import com.gmall.bean.ProcessStatus;
import com.gmall.service.OrderService;
import com.gmall.util.ActiveMQUtil;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.jms.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Component
public class OrderConsumer {
    
    @Autowired
    ActiveMQUtil activeMQUtil;
    
    @Reference
    OrderService orderService;
    
    @JmsListener(destination = "PAYMENT_TO_ORDER",containerFactory = "jmsQueueListener")
    public void consumerPayment(MapMessage mapMessage) throws JMSException {
        String orderId = mapMessage.getString("orderId");
        String result = mapMessage.getString("result");
        if ("success".equals(result)){
            System.out.println("订单："+orderId+"完成");
            orderService.updateStatus(orderId, ProcessStatus.PAID);
            sendOrderToWare(orderId);
        }
    }

    private void sendOrderToWare(String orderId) {
        String wareParamJson = initWareParamJson(orderId);
        Connection connection = activeMQUtil.getConnection();
        try {
            Session session = connection.createSession(true,Session.SESSION_TRANSACTED);
            MessageProducer producer = session.createProducer(session.createQueue("ORDER_RESULT_QUEUE"));
            ActiveMQTextMessage activeMQTextMessage = new ActiveMQTextMessage();
            activeMQTextMessage.setText(wareParamJson);
            producer.send(activeMQTextMessage);
            orderService.updateStatus(orderId,ProcessStatus.NOTIFIED_WARE);
            session.commit();
            connection.close();
        } catch (JMSException e) {
            e.printStackTrace();
        }


    }

    public String initWareParamJson(String orderId){
        OrderInfo orderInfo = orderService.getOrderInfo(orderId);
        
        Map paramMap= new HashMap();
        
        paramMap.put("orderId",orderId);
        paramMap.put("consignee",orderInfo.getConsignee());
        paramMap.put("consigneeTel",orderInfo.getConsigneeTel());
        paramMap.put("orderComment",orderInfo.getOrderComment());
        paramMap.put("orderBody",orderInfo.genSubject());
        paramMap.put("deliveryAddress",orderInfo.getDeliveryAddress());
        paramMap.put("paymentWay","2");
        List<Map> details=new ArrayList();
        for (OrderDetail orderDetail : orderInfo.getOrderDetailList()) {
            HashMap<String, String> orderDetailMap = new HashMap<>();
            orderDetailMap.put("skuId",orderDetail.getSkuId());
            orderDetailMap.put("skuNum",orderDetail.getSkuNum().toString());
            orderDetailMap.put("skuName",orderDetail.getSkuName());
            details.add(orderDetailMap);
        }
        paramMap.put("details",details);
        String paramJson = JSON.toJSONString(paramMap);
        return paramJson;
    }
    @JmsListener(destination = "SKU_DEDUCT_QUEUE",containerFactory = "jmsQueueListener" )
    public  void consumeWareDeduct(MapMessage mapMessage) throws JMSException {
        String orderId = mapMessage.getString("orderId");
        String status = mapMessage.getString("status");
        if ("DEDUCTED".equals(status)) {
            orderService.updateStatus(orderId,ProcessStatus.WAITING_DELEVER);
        }else{
            orderService.updateStatus(orderId,ProcessStatus.STOCK_EXCEPTION);
        }
    }

    @JmsListener(destination = "SKU_DELIVER_QUEUE",containerFactory = "jmsQueueListener" )
    public void consumeDeliver(MapMessage mapMessage) throws JMSException {
        String orderId = mapMessage.getString("orderId");
        String status = mapMessage.getString("status");
        String trackingNo = mapMessage.getString("trackingNo");

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setTrackingNo(trackingNo);

        if (status.equals("DELEVERED")){
            orderService.updateStatus(orderId,ProcessStatus.DELEVERED,orderInfo);
        }
        new ThreadPoolTaskExecutor();

        ScheduledExecutorService executorService;
        Executors.newFixedThreadPool(1);
    }
}
