package com.project.java.entity;

import com.project.java.orm.Colum;
import com.project.java.orm.Id;
import com.project.java.orm.Table;

import java.util.UUID;

@Table(name = "userss")
public class User {
    @Id
    private UUID id;

    @Colum(name = "first_name")
    private String firstName;

    @Colum(name = "last_name")
    private String lastName;

    public User() {
    }

    public User(UUID id, String firstName, String lastName) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                '}';
    }

    public Object getId() {
        return id;
    }

}
