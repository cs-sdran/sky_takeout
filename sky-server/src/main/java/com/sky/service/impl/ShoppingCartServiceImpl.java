package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    public void add(ShoppingCartDTO shoppingCartDTO) {

        //判断商品是否存在

        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);

        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if (list != null && list.size() > 0) {
            ShoppingCart cartService = list.get(0);
            cartService.setNumber(cartService.getNumber() + 1);
            shoppingCartMapper.updateNumberById(cartService);

        }
        else {
            Long dishId = shoppingCartDTO.getDishId();
            Long setmealId = shoppingCartDTO.getSetmealId();

            if(dishId!=null)
            {
                Dish dish = dishMapper.getById(dishId);
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());
            }
            else {
                Setmeal setmeal = setmealMapper.getById(setmealId);
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());
            }
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(shoppingCart);
        }
    }

    @Override
    public List<ShoppingCart> ShowShoppingCart() {
        long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = ShoppingCart.builder().userId(userId).build();
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        return list;

    }

    @Override
    public void sub(ShoppingCartDTO shoppingCartDTO) {
        long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = ShoppingCart.builder().userId(userId).dishId(shoppingCartDTO.getDishId()).setmealId(shoppingCartDTO.getSetmealId()).build();
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if (list != null && list.size() > 0) {
            ShoppingCart cartService = list.get(0);
            if (cartService.getNumber() > 0) {
                cartService.setNumber(cartService.getNumber() - 1);
                if(cartService.getNumber() == 0)
                    shoppingCartMapper.deleteByUserIdAndDishIdOrSetmealId(cartService);
                shoppingCartMapper.updateNumberById(cartService);
            } else {
                shoppingCartMapper.deleteByUserIdAndDishIdOrSetmealId(cartService);
            }
        }

    }

    @Override
    public void clean() {
        long userId = BaseContext.getCurrentId();
        shoppingCartMapper.deleteByUserId(userId);
        log.info("用户：{}，清空购物车成功", userId);

    }
}
