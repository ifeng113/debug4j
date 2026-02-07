package com.k4ln.demo2.controller;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class DemoNoBeanDto {

    private String noBeanValue;

    public DemoNoBeanDto(String noBeanValue) {
        this.noBeanValue = noBeanValue;
    }

    public void noBeanTest() {
        try {
            Thread.sleep(102);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("noBeanValue:{}", noBeanValue);
    }
}
