package com.sky.service;

import com.sky.dto.DishDTO;

public interface DishService {
    /**
     * 新增菜品，同时插入菜品对应的数据，需要操作两张表：dish、dish_flavor
     * @param dishDTO
     */
    public void saveWithFlavor(DishDTO dishDTO);
}
