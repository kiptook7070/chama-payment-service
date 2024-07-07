package com.eclectics.chamapayments.service.cache;

import com.eclectics.chamapayments.repository.AccountsRepository;
import com.eclectics.chamapayments.repository.ContributionRepository;
import com.eclectics.chamapayments.repository.LoanproductsRepository;
import com.eclectics.chamapayments.util.MapperFunction;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author kiptoo joshua
 * @createdOn 01/03/2024
 **/
@Service
@RequiredArgsConstructor
public class CacheService {
    private final MapperFunction mapperFunction;
    private final ReactiveStringRedisTemplate redisOperations;
    private final StringRedisTemplate stringRedisTemplate;
    private final AccountsRepository accountsRepository;
    private final ContributionRepository contributionRepository;
    private final LoanproductsRepository loanproductsRepository;
    private ReactiveHashOperations<String, String, String> hashOperations;
    private HashOperations<String, String, String> syncHashOperations;

    @PostConstruct
    private void init() {
        hashOperations = redisOperations.opsForHash();
        syncHashOperations = stringRedisTemplate.opsForHash();
    }

    private final Gson gson = new Gson();
    private static final String CACHE_NAME = "chama-cache-postbank";

    @Scheduled(fixedDelay = 3000)
    public void publishGroupAccountsInfoToRedis() {
        Mono.fromRunnable(() -> {
            List<String> groupAccountData = accountsRepository.findAllBySoftDeleteFalseOrderByGroupIdAsc()
                    .stream()
                    .filter(group -> !group.isSoftDelete())
                    .map(mapperFunction.mapAccountsToWrapper())
                    .map(gson::toJson)
                    .collect(Collectors.toList());
            if (!groupAccountData.isEmpty()) {
                hashOperations.put(CACHE_NAME, "groups-account-data", gson.toJson(groupAccountData))
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();
            }
        }).publishOn(Schedulers.boundedElastic()).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    @Scheduled(fixedDelay = 3000)
    public void publishGroupsContributionsInfoToRedis() {
        Mono.fromRunnable(() -> {
            List<String> groupContributionsData = contributionRepository.findAllBySoftDeleteFalseOrderByGroupIdAsc()
                    .stream()
                    .filter(contrib -> !contrib.isSoftDelete())
                    .map(mapperFunction.mapGroupContributionsToWrapper())
                    .map(gson::toJson)
                    .collect(Collectors.toList());
            if (!groupContributionsData.isEmpty()) {
                hashOperations.put(CACHE_NAME, "groups-contributions-data", gson.toJson(groupContributionsData))
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();
            }
        }).publishOn(Schedulers.boundedElastic()).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    @Scheduled(fixedDelay = 3000)
    public void publishGroupsLoanProductsInfoToRedis() {
        Mono.fromRunnable(() -> {
            List<String> groupLoanProductData = loanproductsRepository.findAllBySoftDeleteFalseOrderByGroupIdAsc()
                    .stream()
                    .filter(contrib -> !contrib.isSoftDelete())
                    .map(mapperFunction.mapToGroupLoanProductWrapper())
                    .map(gson::toJson)
                    .collect(Collectors.toList());
            if (!groupLoanProductData.isEmpty()) {
                hashOperations.put(CACHE_NAME, "groups-loan-product-data", gson.toJson(groupLoanProductData))
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();
            }
        }).publishOn(Schedulers.boundedElastic()).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }
}
