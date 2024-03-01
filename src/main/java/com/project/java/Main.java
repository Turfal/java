package com.project.java;

import java.sql.SQLException;
import java.util.List;

import com.project.java.orm.DatabaseManager;
import com.project.java.entity.User;

public class Main {
    public static void main(String[] args) {
        DatabaseManager databaseManager = new DatabaseManager();

        try {

            databaseManager.createTable(User.class);


            User user = new User(null, "UWU", "UWU");
            databaseManager.save(user);


            User foundUser = databaseManager.findById(User.class, user.getId()).orElse(null);
            System.out.println("Found user: " + foundUser);


            List<User> users = databaseManager.findAll(User.class);
            System.out.println("All users: " + users);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

