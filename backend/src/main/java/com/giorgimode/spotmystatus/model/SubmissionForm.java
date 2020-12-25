package com.giorgimode.spotmystatus.model;

import lombok.Value;

@Value
public class SubmissionForm {

    String name;
    String email;
    String subject;
    String message;
}
