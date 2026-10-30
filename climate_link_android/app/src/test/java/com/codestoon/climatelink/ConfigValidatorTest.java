package com.codestoon.climatelink;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class ConfigValidatorTest {
    private ConfigValidator.Input form(String ssid,String password,String url,String token,String sample,String every,boolean clear,boolean http){
        return new ConfigValidator.Input(ssid,password,url,token,sample,every,clear,false,http);
    }
    @Test public void acceptsPersianDigitsAndPreservedSecrets(){
        assertTrue(ConfigValidator.validate(form("شبکه","","https://example.com/api","","۹۰۰","۳۲",false,false)).isEmpty());
    }
    @Test public void reportsEveryInvalidFieldTogether(){
        Map<String,Integer> errors=ConfigValidator.validate(form("","short","bad url","a\nb","0","33",false,false));
        assertEquals(6,errors.size());
        assertTrue(errors.containsKey("ssid"));assertTrue(errors.containsKey("token"));
    }
    @Test public void countsUtf8BytesForNetworkNames(){
        assertFalse(ConfigValidator.validate(form("ش".repeat(17),"","https://example.com","","60","1",false,false)).isEmpty());
        assertTrue(ConfigValidator.validate(form("ش".repeat(16),"","https://example.com","","60","1",false,false)).isEmpty());
    }
    @Test public void localHttpRequiresExplicitChoice(){
        assertTrue(ConfigValidator.validate(form("wifi","","http://192.168.1.10:8080/api","","60","1",false,false)).containsKey("url"));
        assertFalse(ConfigValidator.validate(form("wifi","","http://192.168.1.10:8080/api","","60","1",false,true)).containsKey("url"));
    }
    @Test public void rejectsEmbeddedCredentialsAndFragments(){
        for(String url:new String[]{"https://user:pass@example.com","https://example.com/#fragment"})
            assertTrue(ConfigValidator.validate(form("wifi","",url,"","60","1",false,false)).containsKey("url"));
    }
    @Test public void rejectsConflictingCredentialReset(){
        assertEquals(Integer.valueOf(R.string.clear_error),ConfigValidator.validate(form("wifi","valid-pass","https://example.com","","60","1",true,false)).get("password"));
    }
}
