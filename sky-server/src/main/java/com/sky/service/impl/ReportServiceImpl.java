package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);

        while (!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        List<Double> turnoverList = new ArrayList<>();

        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map map = new HashMap();
            map.put("begin", beginTime);
            map.put("end", endTime);
            map.put("status", Orders.COMPLETED);

            Double turnover = orderMapper.sumByMap(map);

            turnover = turnover == null ? 0.0 : turnover;

            turnoverList.add(turnover);
        }

        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();
    }

    /**
     * 用户统计
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        UserReportVO userReportVO = new UserReportVO();
        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);
        while (!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        // 用户总数
        List<Integer> totalUserList = new ArrayList<>();
        // 新增用户数
        List<Integer> newUserList = new ArrayList<>();
        for(LocalDate date : dateList)
        {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            Map map = new HashMap();
            map.put("end", endTime);
            Integer totalUser = userMapper.countByMap(map);// 用户总数

            map.put("begin", beginTime);
            Integer newUser = userMapper.countByMap(map);// 新增用户数
            totalUserList.add(totalUser);
            newUserList.add(newUser);
        }
        return userReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .build();
    }

    @Override
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        OrderReportVO orderReportVO = new OrderReportVO();
        List<LocalDate> datelist=new ArrayList<>();
        datelist.add(begin);
        while(!begin.equals(end))
        {
            begin=begin.plusDays(1);
            datelist.add(begin);
        }
        List<Integer> orderCountList=new ArrayList<>();
        List<Integer> validOrderCountList=new ArrayList<>();
        for(LocalDate localDate:datelist)
        {
            LocalDateTime beginTime = LocalDateTime.of(localDate, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(localDate, LocalTime.MAX);

            Integer orderCount = getOrderCount(beginTime, endTime, null);
            orderCountList.add(orderCount);// 订单总数
            Integer validOrderCount = getOrderCount(beginTime, endTime, Orders.COMPLETED);
            validOrderCountList.add(validOrderCount);// 有效订单数
        }

        Integer totalOrderCount=orderCountList.stream().reduce(Integer::sum).get();
        Integer validOrderCount=validOrderCountList.stream().reduce(Integer::sum).get();
        Double orderCompletionRate = 0.0;
        if(totalOrderCount != 0)
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;


        return  OrderReportVO.builder()
                .dateList(StringUtils.join(datelist,","))
                .orderCountList(StringUtils.join(orderCountList,","))
                .validOrderCountList(StringUtils.join(validOrderCountList,","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }



    private Integer getOrderCount(LocalDateTime beginTime, LocalDateTime endTime,Integer status)
    {
        Map map = new HashMap();
        map.put("begin", beginTime);
        map.put("end", endTime);
        map.put("status", status);
        return orderMapper.countByMap(map);
    }

    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);


        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(beginTime, endTime);
        List<String> names =salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        String namelist=StringUtils.join(names,",");
        List<Integer> numbers = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        String numberlist=StringUtils.join(numbers,",");


        return SalesTop10ReportVO.builder()
                .nameList(namelist)
                .numberList(numberlist)
                .build();

    }
}
