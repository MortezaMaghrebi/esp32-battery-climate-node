package com.codestoon.climatelink;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

/** Form validation shared by submit and field-level feedback. No device or network calls. */
final class ConfigValidator {
    static final class Input {
        final String ssid,password,url,token,sample,every;
        final boolean clearPassword,clearToken,allowHttp;
        Input(String ssid,String password,String url,String token,String sample,String every,
                boolean clearPassword,boolean clearToken,boolean allowHttp){
            this.ssid=ssid;this.password=password;this.url=url;this.token=token;
            this.sample=sample;this.every=every;this.clearPassword=clearPassword;
            this.clearToken=clearToken;this.allowHttp=allowHttp;
        }
    }
    private static int bytes(String text){return text.getBytes(StandardCharsets.UTF_8).length;}
    private static boolean integerIn(String value,int low,int high){
        try{int n=Integer.parseInt(value);return n>=low&&n<=high;}catch(NumberFormatException e){return false;}
    }
    static LinkedHashMap<String,Integer> validate(Input i){
        LinkedHashMap<String,Integer> errors=new LinkedHashMap<>();
        if(i.ssid.isEmpty()||bytes(i.ssid)>32)errors.put("ssid",R.string.ssid_error);
        if(!i.password.isEmpty()&&(bytes(i.password)<8||bytes(i.password)>63))errors.put("password",R.string.password_error);
        if(i.clearPassword&&!i.password.isEmpty())errors.put("password",R.string.clear_error);
        if(bytes(i.url)>192)errors.put("url",R.string.url_length_error);
        else try{
            URI uri=URI.create(i.url);
            if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||
                !("https".equals(uri.getScheme())||(i.allowHttp&&"http".equals(uri.getScheme()))))
                errors.put("url",R.string.url_error);
        }catch(IllegalArgumentException e){errors.put("url",R.string.url_error);}
        if(bytes(i.token)>128||i.token.contains("\r")||i.token.contains("\n"))errors.put("token",R.string.token_error);
        if(i.clearToken&&!i.token.isEmpty())errors.put("token",R.string.clear_error);
        if(!integerIn(i.sample,60,86400))errors.put("sample",R.string.sample_interval_error);
        if(!integerIn(i.every,1,32))errors.put("every",R.string.upload_every_error);
        return errors;
    }
}
