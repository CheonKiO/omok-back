package org.scoula.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @Scheduled(EmptyRoomCleaner) 활성화 + 게임 시작 지연 등에 쓰는 공용 TaskScheduler 제공 (#6).
 * daemon 스레드 풀이라 종료 시 JVM 종료를 막지 않는다(과거 게임마다 non-daemon Timer 누적 문제 해소).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("room-sched-");
        scheduler.setDaemon(true);
        return scheduler;
    }
}
