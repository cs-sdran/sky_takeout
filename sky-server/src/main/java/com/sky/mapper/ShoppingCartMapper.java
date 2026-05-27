package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {

    List<ShoppingCart> list(ShoppingCart shoppingCart);

    @Update("update shopping_cart set number = #{number} where id = #{id}")
    void updateNumberById(ShoppingCart shoppingCart);


    @Insert("insert into shopping_cart (name, image, dish_id, setmeal_id, dish_flavor, number, create_time,user_id,amount) " +
            "VALUES (#{name}, #{image}, #{dishId}, #{setmealId}, #{dishFlavor}, #{number}, #{createTime},#{userId},#{amount})")
    void insert(ShoppingCart shoppingCart);

    @Delete("delete from shopping_cart where user_id = #{userId} and dish_id = #{dishId} and setmeal_id = #{setmealId}")
    void deleteByUserIdAndDishIdOrSetmealId(ShoppingCart cartService);

    @Delete("delete from shopping_cart where user_id = #{userId}")
    void deleteByUserId(Long userId);

    @Select("select * from shopping_cart where user_id = #{userId}")
    ShoppingCart getByUserId(long uerId);

    void insertBatch(@Param("shoppingCartList") List<ShoppingCart> shoppingCartList);


}
