package com.nemesiss.dev.models;

import com.nemesiss.dev.annotations.BuilderProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author: <a href="yingyin.lsy@alibaba-inc.com">萤音</a>
 * @date: 2020/7/9
 * @time: 15:57
 * @description:
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Animal {

    @BuilderProperty
    String name;

    @BuilderProperty
    boolean canFly;
}
