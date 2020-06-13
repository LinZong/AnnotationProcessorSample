package com.nemesiss.dev.models;


import com.nemesiss.dev.annotations.BuilderProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Person {

    @BuilderProperty
    private String name;
}
