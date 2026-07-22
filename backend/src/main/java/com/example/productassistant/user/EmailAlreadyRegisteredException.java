package com.example.productassistant.user;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("该邮箱已注册");
    }
}
