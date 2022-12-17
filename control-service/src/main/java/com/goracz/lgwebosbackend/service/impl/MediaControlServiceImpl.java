package com.goracz.lgwebosbackend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goracz.lgwebosbackend.dto.media.volume.SetVolumeDto;
import com.goracz.lgwebosbackend.exception.KafkaConsumeFailException;
import com.goracz.lgwebosbackend.model.EventCategory;
import com.goracz.lgwebosbackend.model.EventMessage;
import com.goracz.lgwebosbackend.model.response.GetVolumeResponse;
import com.goracz.lgwebosbackend.service.CacheManager;
import com.goracz.lgwebosbackend.service.EventService;
import com.goracz.lgwebosbackend.service.MediaControlService;
import lombok.Getter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Service
public class MediaControlServiceImpl implements MediaControlService {
    private static final String MEDIA_VOLUME_CACHE_KEY = "media:volume";

    private final EventService<EventMessage<GetVolumeResponse>> eventService;
    private final WebClient webClient;
    private final CacheManager<String, GetVolumeResponse> cacheManager;
    @Getter
    private final Sinks.Many<GetVolumeResponse> volumeStream = Sinks.many().multicast().onBackpressureBuffer();

    public MediaControlServiceImpl(
            EventService<EventMessage<GetVolumeResponse>> eventService,
            WebClient webClient,
            CacheManager<String, GetVolumeResponse> cacheManager) {
        this.eventService = eventService;
        this.webClient = webClient;
        this.cacheManager = cacheManager;
    }

    @Override
    public Mono<GetVolumeResponse> getVolume() {
        return this.readVolumeFromCache()
                .switchIfEmpty(this.getVolumeFromTv())
                .flatMap(this::writeVolumeToCache);
    }

    private Mono<GetVolumeResponse> readVolumeFromCache() {
        return this.cacheManager.read(MEDIA_VOLUME_CACHE_KEY);
    }

    private Mono<GetVolumeResponse> getVolumeFromTv() {
        return this.webClient
                .get()
                .uri("/media/volume")
                .retrieve()
                .bodyToMono(GetVolumeResponse.class);
    }

    private Mono<GetVolumeResponse> writeVolumeToCache(GetVolumeResponse volume) {
        return this.cacheManager.write(MEDIA_VOLUME_CACHE_KEY, volume);
    }

    @Override
    public Mono<Void> increaseVolume() {
        return this.webClient
                .post()
                .uri("/media/volume/up")
                .retrieve()
                .bodyToMono(Void.class)
                .log();
    }

    @Override
    public Mono<Void> decreaseVolume() {
        return this.webClient
                .post()
                .uri("/media/volume/down")
                .retrieve()
                .bodyToMono(Void.class)
                .log();
    }

    @Override
    public Mono<Object> setVolume(SetVolumeDto setVolumeDto) {
        return this.webClient
                .post()
                .uri("/media/volume")
                .bodyValue(setVolumeDto)
                .retrieve()
                .bodyToMono(Object.class)
                .log();
    }

    @KafkaListener(topics = "volume-change")
    public void onVolumeChange(ConsumerRecord<String, String> message) throws KafkaConsumeFailException {
        try {
            this.handleVolumeChange(message).subscribe();
        } catch (Exception exception) {
            throw new KafkaConsumeFailException(exception.getMessage());
        }
    }

    private Mono<Sinks.EmitResult> handleVolumeChange(ConsumerRecord<String, String> message) {
        return this.getVolumeFromMqMessage(message)
                .flatMap(this::writeVolumeToCache)
                .flatMap(this::notifyListenersAboutVolumeChange);
    }

    private Mono<GetVolumeResponse> getVolumeFromMqMessage(ConsumerRecord<String, String> message) {
        return Mono.fromCallable(() -> new ObjectMapper().readValue(message.value(), GetVolumeResponse.class));
    }

    private Mono<Sinks.EmitResult> notifyListenersAboutVolumeChange(GetVolumeResponse volume) {
        return Mono.fromCallable(() -> new EventMessage<>(EventCategory.VOLUME_CHANGED, volume))
                .flatMap(eventMessage -> this.eventService.emit(eventMessage, eventMessage.getCategory()));
    }
}