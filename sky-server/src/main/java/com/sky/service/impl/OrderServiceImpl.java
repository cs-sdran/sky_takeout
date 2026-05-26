package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Constant;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private WeChatPayUtil weChatPayUtil;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        AddressBook addressBook=addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook==null)//地址蒲为空
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);

        ShoppingCart shoppingCart = new ShoppingCart();
        long userId= BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);
       if(shoppingCartMapper.list(shoppingCart)==null)//购物车为空
       {
           throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
       }


    //向订单表插入一条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);
        orderMapper.insert(orders);

        //向订单明细表插入n条数据
        List<OrderDetail> orderDetails = new ArrayList<>();
        for (ShoppingCart cartService : shoppingCartMapper.list(shoppingCart)) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cartService,orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetails.add(orderDetail);

        }
        orderDetailMapper.insertBatch(orderDetails);


        shoppingCartMapper.deleteByUserId(userId);
        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderTime(orders.getOrderTime())
                .orderAmount(orders.getAmount())
                .build();

    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
      /*  JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );*/
        JSONObject jsonObject = new JSONObject();

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    @Override
    public PageResult historyOrders(Integer page, Integer pageSize,Integer status) {

        long userId = BaseContext.getCurrentId();

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(userId);
        ordersPageQueryDTO.setStatus(status);
        PageHelper.startPage(page,pageSize);
        Page<Orders> pagereult=orderMapper.pageQuery(ordersPageQueryDTO);

        ArrayList<OrderVO> orderVOS = new ArrayList<>();
        if(pagereult.size()!=0&&pagereult.getTotal()>0)
        {
            for(Orders orders:pagereult)
            {
                OrderVO orderVO = new OrderVO();
                long id=orders.getId();
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
                orderVO.setOrderDetailList(orderDetailList);
                BeanUtils.copyProperties(orders,orderVO);

               orderVOS.add(orderVO);

            }
        }

        return new PageResult(pagereult.getTotal(), orderVOS);

    }

    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {

        //VO对象包含order和菜品信息
        long id=BaseContext.getCurrentId();
        ordersPageQueryDTO.setUserId(id);

        ArrayList<OrderVO> orderVOS = new ArrayList<>();
        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());
        Page<Orders> pagereult=orderMapper.pageQuery(ordersPageQueryDTO);
       for(Orders orders:pagereult)
       {
           OrderVO orderVO = new OrderVO();
           BeanUtils.copyProperties(orders,orderVO);
           String orderDishes = orderDetailMapper.getByOrderId(orders.getId()).toString();
           orderVO.setOrderDishes(orderDishes);
           orderVOS.add(orderVO);
       }

       return new PageResult(pagereult.getTotal(), orderVOS);
    }

    @Override
    public OrderStatisticsVO statistics() {
        // 根据状态，分别查询出待接单、待派送、派送中的订单数量
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        // 将查询出的数据封装到orderStatisticsVO中响应
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    @Override
    public OrderVO orderDetail(long id) {
        OrderVO orderVO=new OrderVO();
        Orders orders = orderMapper.getById(id);
        List<OrderDetail> orderDetailList=orderDetailMapper.getByOrderId(id);
        orderVO.setOrderDetailList(orderDetailList);
        BeanUtils.copyProperties(orders,orderVO);

        return orderVO;
    }

    @Override
    public void cancel(long id) {
        //查询当前订单是否存在
        Orders ordersis=orderMapper.getById(id);
        if(ordersis==null)
            throw  new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        //判断当前订单是否可取消 若处于配送中或已结单则不可取消
        if(ordersis.getStatus()==Orders.DELIVERY_IN_PROGRESS||ordersis.getStatus()==Orders.COMPLETED)
        {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //若处于待付款或待接单即可取消，取消时将订单状态设置为已取消
        //若处于待接单，则需要退款
        Orders orders=new Orders();
        orders.setId(ordersis.getId());

        // 订单处于待接单状态下取消，需要进行退款
       /* if (ordersis.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            //调用微信支付退款接口
            weChatPayUtil.refund(
                    ordersis.getNumber(), //商户订单号
                    ordersis.getNumber(), //商户退款单号
                    new BigDecimal(0.01),//退款金额，单位 元
                    new BigDecimal(0.01));//原订单金额

            //支付状态修改为 退款
            orders.setPayStatus(Orders.REFUND);
        }*/

        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);

    }

    @Override
    public void repetition(long id) {//传入的Id是订单id
        // 查询当前用户id
        Long userId = BaseContext.getCurrentId();

        // 根据订单id查询当前订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 将订单详情对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            // 将原订单详情里面的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        // 将购物车对象批量添加到数据库
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

}
