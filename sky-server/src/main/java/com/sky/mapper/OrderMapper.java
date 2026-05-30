package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.OrdersDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import com.sky.vo.OrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.core.annotation.Order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {

    void insert(Orders orders);
    /**
     * 根据订单号查询订单
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);

    /**
     * 分页查询订单列表
     * @param ordersPageQueryDTO
     * @return
     */

    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    @Select("select count(id) from orders where status = #{status}")
    Integer countStatus(Integer status);

    @Select("select  * from orders where id=#{id}")
    Orders getById(Long id);

    @Update("update status from orders where  id=#{id}")
    void cancel(long id);

    @Select("select * from orders where status =#{status} and order_time < #{orderTime}")
    List<Orders> getByStatusAndOrderTimeLT(Integer status, LocalDateTime orderTime);
    /**
     * 根据状态统计订单数量
     * @param map
     * @return
     */
    Double sumByMap(Map map);
    /**
     * 根据状态统计订单数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);

    List<GoodsSalesDTO> getSalesTop10(@Param("begin") LocalDateTime begin,
                                      @Param("end") LocalDateTime end);
}
