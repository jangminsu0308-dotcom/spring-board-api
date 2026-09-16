package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		// WSL2(mirrored 네트워킹) 등 일부 환경에서 JVM이 서버 소켓을 IPv6로만 바인딩해
		// 127.0.0.1(IPv4) 접속이 막히는 문제가 있어, IPv4를 우선하도록 강제한다.
		System.setProperty("java.net.preferIPv4Stack", "true");
		SpringApplication.run(DemoApplication.class, args);
	}

}
