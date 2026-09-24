package cn.qwenpaw.android;

import org.json.*;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class UpdateReleaseTest {
    private JSONObject release(long code,String pkg) throws Exception {
        return new JSONObject().put("bundle_id",pkg).put("release_version","0.7.1").put("build_version",String.valueOf(code))
            .put("size",12345).put("install_url",UpdateRelease.ORIGIN+"/android/1/download")
            .put("custom_fields",new JSONArray().put(new JSONObject().put("name","SHA256").put("value","a".repeat(64))));
    }
    private JSONObject response(JSONObject... items)throws Exception {return new JSONObject().put("releases",new JSONArray(items));}
    private JSONObject field(JSONObject release,String name,String value)throws Exception {
        release.getJSONArray("custom_fields").put(new JSONObject().put("name",name).put("value",value));return release;
    }
    @Test public void choosesHighestVersionAndIgnoresOtherPackages()throws Exception {
        UpdateRelease result=UpdateRelease.latest(response(release(13,"cn.qwenpaw.android"),release(99,"other"),release(14,"cn.qwenpaw.android")),"cn.qwenpaw.android",12);
        assertEquals(14,result.code);
    }
    @Test public void neverOffersDowngradeOrSameVersion()throws Exception {
        assertNull(UpdateRelease.latest(response(release(11,"cn.qwenpaw.android"),release(12,"cn.qwenpaw.android")),"cn.qwenpaw.android",12));
    }
    @Test public void normalUpgradeHasNoThresholds()throws Exception {
        assertEquals(UpdateRelease.Mode.NORMAL,UpdateRelease.latest(response(release(17,"cn.qwenpaw.android")),"cn.qwenpaw.android",16,1).mode);
    }
    @Test public void recommendedThresholdHandlesCrossVersionUpgrade()throws Exception {
        JSONObject release=field(release(17,"cn.qwenpaw.android"),"minimumRecommendedVersionCode","16");
        assertEquals(UpdateRelease.Mode.RECOMMENDED,UpdateRelease.latest(response(release),"cn.qwenpaw.android",15,1).mode);
        assertEquals(UpdateRelease.Mode.NORMAL,UpdateRelease.latest(response(release),"cn.qwenpaw.android",16,1).mode);
    }
    @Test public void requiredThresholdTakesPriority()throws Exception {
        JSONObject release=field(field(release(17,"cn.qwenpaw.android"),"minimumSupportedVersionCode","16"),"minimumRecommendedVersionCode","17");
        assertEquals(UpdateRelease.Mode.REQUIRED,UpdateRelease.latest(response(release),"cn.qwenpaw.android",15,1).mode);
        assertEquals(UpdateRelease.Mode.RECOMMENDED,UpdateRelease.latest(response(release),"cn.qwenpaw.android",16,1).mode);
    }
    @Test public void forceDateStartsAsRecommendationThenBecomesRequired()throws Exception {
        JSONObject release=field(field(release(17,"cn.qwenpaw.android"),"minimumSupportedVersionCode","16"),"forceAfter","2026-10-01T00:00:00+08:00");
        assertEquals(UpdateRelease.Mode.RECOMMENDED,UpdateRelease.latest(response(release),"cn.qwenpaw.android",15,1).mode);
        assertEquals(UpdateRelease.Mode.REQUIRED,UpdateRelease.latest(response(release),"cn.qwenpaw.android",15,2_000_000_000L).mode);
    }
    @Test(expected=IOException.class) public void rejectsInvertedPolicyThresholds()throws Exception {
        JSONObject release=field(field(release(17,"cn.qwenpaw.android"),"minimumSupportedVersionCode","17"),"minimumRecommendedVersionCode","16");
        UpdateRelease.latest(response(release),"cn.qwenpaw.android",15,1);
    }
    @Test(expected=IOException.class) public void rejectsThresholdAboveRelease()throws Exception {
        UpdateRelease.latest(response(field(release(17,"cn.qwenpaw.android"),"minimumSupportedVersionCode","18")),"cn.qwenpaw.android",15,1);
    }
    @Test(expected=IOException.class) public void rejectsUnknownPolicySchema()throws Exception {
        UpdateRelease.latest(response(field(release(17,"cn.qwenpaw.android"),"updatePolicySchema","2")),"cn.qwenpaw.android",15,1);
    }
    @Test public void noUpdateIsAnEmptyReleaseArray()throws Exception {assertNull(UpdateRelease.latest(response(),"cn.qwenpaw.android",12));}
    @Test(expected=IOException.class) public void rejectsMissingHash()throws Exception {
        UpdateRelease.latest(response(release(13,"cn.qwenpaw.android").put("custom_fields",new JSONArray())),"cn.qwenpaw.android",12);
    }
    @Test(expected=IOException.class) public void rejectsMalformedResponse()throws Exception {UpdateRelease.latest(new JSONObject(),"cn.qwenpaw.android",12);}
    @Test(expected=IOException.class) public void rejectsOversizePackage()throws Exception {
        UpdateRelease.latest(response(release(13,"cn.qwenpaw.android").put("size",UpdateRelease.MAX_APK_BYTES+1)),"cn.qwenpaw.android",12);
    }
    @Test public void downloadMustUseTrustedHttpsOrigin() {
        assertTrue(UpdateRelease.safeUrl(UpdateRelease.ORIGIN+"/a.apk"));
        assertFalse(UpdateRelease.safeUrl(UpdateRelease.ORIGIN.replace("https://","http://")+"/a.apk"));
        assertFalse(UpdateRelease.safeUrl(UpdateRelease.ORIGIN+".evil.test/a.apk"));
        assertFalse(UpdateRelease.safeUrl("https://evil.test/a.apk"));
        assertFalse(UpdateRelease.safeUrl(UpdateRelease.ORIGIN.replace("https://","https://user@")+"/a.apk"));
        assertFalse(UpdateRelease.safeUrl(UpdateRelease.ORIGIN+":444/a.apk"));
    }
}
