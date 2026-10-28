package com.urbantransport.finance;

import org.springframework.boot.SpringApplication;

public class TestFinanceApplication {

	public static void main(String[] args) {
		SpringApplication.from(FinanceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
