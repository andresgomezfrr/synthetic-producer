package net.redborder.utils.producers;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

public class HttpProducer implements IProducer {
    String endpoint;

    HttpClient httpClient = HttpClientBuilder.create().build(); //Use this instead


    public HttpProducer(String endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public void send(String message, String key) {
        try {

            HttpPost request = new HttpPost(endpoint);
            StringEntity params =new StringEntity(message);
            request.addHeader("content-type", "application/x-www-form-urlencoded");
            request.setEntity(params);
            httpClient.execute(request);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getPartitionKey() {
        return null;
    }
}
