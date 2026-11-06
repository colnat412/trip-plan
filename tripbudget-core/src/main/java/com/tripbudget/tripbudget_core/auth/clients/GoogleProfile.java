package com.tripbudget.tripbudget_core.auth.clients;


public record GoogleProfile(
        String email, String name, String picture
) {}
