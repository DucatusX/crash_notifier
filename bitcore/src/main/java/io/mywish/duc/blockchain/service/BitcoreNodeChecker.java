package io.mywish.duc.blockchain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mywish.duc.blockchain.model.DucBlock;
import io.mywish.duc.blockchain.model.DucatusCondition;
import io.mywish.event.model.ConnectionCrushEvent;
import io.mywish.event.service.EventPublisher;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;

@Slf4j
public class BitcoreNodeChecker {
    @Autowired
    private DucatusCondition condition;
    private Integer lastBlock;
    private String uri;
    private CloseableHttpClient client;
    @Autowired
    private EventPublisher publisher;

    public BitcoreNodeChecker() {
        uri = "https://ducapi.rocknblock.io/api/DUC/mainnet/block?limit=1";
        client = HttpClientBuilder.create()
                .setMaxConnTotal(100)
                .build();
    }

    @Scheduled(cron = "${io.mywish.duc.blockchain.cron.sheduler}")
    public void doCheck() {
        doConnect();
        doRead();
    }

    private void doRead() {
        log.info("start sheduling ducatus request");
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            log.info("try to connect ducatus bitcore");
            DucBlock[] ducBlocks = objectMapper.readValue(new URL(uri), DucBlock[].class);
            lastBlock = ducBlocks[0].getHeight();
        } catch (IOException e) {
            log.warn("can't to connect to ducatus bitcore with Exception message: {}", e.getMessage());
            log.warn("Exception stack trace: {}", Arrays.toString(e.getStackTrace()));
            e.printStackTrace();
        } finally {
            condition.setConditions(lastBlock == null ? condition.getLastBlock() : lastBlock);
        }
    }

    @SneakyThrows
    private void doConnect() {
        CloseableHttpResponse response = null;
        try {
            response = client.execute(HttpHost.create(uri), new HttpGet());
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (response != null) {
            int code = response.getStatusLine().getStatusCode();
            if (code != 200 && code > 0) {
                log.warn("Status code is {}", code);
                publisher.publish(
                        new ConnectionCrushEvent(
                                String.format("Can't connect to ducapi.rocknblock.io. Code status %d", code)
                        ));
            }
        }
    }
}
