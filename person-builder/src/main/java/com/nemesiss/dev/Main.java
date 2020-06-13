package com.nemesiss.dev;

import com.nemesiss.dev.models.Person;
import com.nemesiss.dev.models.PersonBuilder;

public class Main {
    public static void main(String[] args) {
        Person person = new Person("Hello");

        Person person2 = new PersonBuilder()
                .setName("Hello")
                .build();

        Person person3 = new PersonBuilder()
                .setName("Hi!")
                .build();

        System.out.println(person.equals(person2));
        System.out.println(person2.equals(person3));
    }
}
