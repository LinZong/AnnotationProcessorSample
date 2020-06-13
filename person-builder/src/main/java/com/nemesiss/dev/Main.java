package com.nemesiss.dev;

import com.nemesiss.dev.models.Person;
import com.nemesiss.dev.models.PersonBuilder;

public class Main {
    public static void main(String[] args) {
        Person person = new Person("Hello",20);

        Person person2 = new PersonBuilder()
                .setName("Hello")
                .setAge(20)
                .build();

        Person person3 = new PersonBuilder()
                .setName("Hi!")
                .setAge(30)
                .build();

        System.out.println(person.equals(person2));
        System.out.println(person2.equals(person3));
    }
}
