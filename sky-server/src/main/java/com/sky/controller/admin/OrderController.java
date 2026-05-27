package com.sky.controller.admin;


import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.*;

@RestController("adminOrderController")
@RequestMapping("/admin/order")
@Slf4j
@Api(tags = "订单管理接口")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/conditionSearch")
    @ApiOperation("订单搜索")
    public Result<PageResult> conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO)
    {
        PageResult pageResult = orderService.conditionSearch(ordersPageQueryDTO);
        return Result.success(pageResult);

    }

    @GetMapping("/statistics")
    @ApiOperation("统计接口")
    public Result<OrderStatisticsVO> statistics()
    {
        OrderStatisticsVO orderStatisticsVO = orderService.statistics();
        return Result.success(orderStatisticsVO);
    }

    @GetMapping("/details/{id}")
    @ApiOperation("订单详情接口")
    public Result<OrderVO>  details(@PathVariable Long id)
    {
        OrderVO orderVO = orderService.orderDetail(id);
        return Result.success(orderVO);

    }
    @PutMapping("/confirm")
    @ApiOperation("确认订单接口")
    public Result confirm(@RequestBody OrdersConfirmDTO ordersConfirmDTO)
    {
        orderService.confirm(ordersConfirmDTO);
        return Result.success();
    }
}
