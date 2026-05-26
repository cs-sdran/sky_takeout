package com.sky.mapper;

import com.sky.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrderDetailMapper {
    /**
     * 批量插入订单明细数据
     * @param orderDetails
     */
    void insertBatch(List<OrderDetail> orderDetails);

    List<OrderDetail> getByOrderId(Long orderId);

    /**
     * 取消订单，修改订单明细的订单ID为null
     * @param orderId
     */
    @Update("update order_detail set order_id = null where order_id = #{orderId}")
    void cancel(Long orderId);
}
