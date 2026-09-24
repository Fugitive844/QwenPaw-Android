package cn.qwenpaw.android;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;

/** Public release metadata only; never carries a publishing or chat credential. */
final class UpdateRelease {
    enum Mode { NORMAL, RECOMMENDED, REQUIRED }
    static final String ORIGIN = BuildConfig.UPDATE_ORIGIN;
    static final long MAX_APK_BYTES = 200L * 1024 * 1024;
    final long code, size;
    final String name, url, sha256, notes, policyMessage;
    final Mode mode;

    UpdateRelease(long code, long size, String name, String url, String sha256, String notes, Mode mode, String policyMessage) {
        this.code=code; this.size=size; this.name=name; this.url=url; this.sha256=sha256; this.notes=notes;
        this.mode=mode; this.policyMessage=policyMessage;
    }

    static String field(JSONObject release, String name) {
        JSONArray fields=release.optJSONArray("custom_fields");
        if(fields!=null) for(int i=0;i<fields.length();i++) {
            JSONObject field=fields.optJSONObject(i);
            if(field!=null && name.equalsIgnoreCase(field.optString("name"))) return field.optString("value");
        }
        return "";
    }

    static UpdateRelease latest(JSONObject response, String packageName, long installed) throws IOException {
        return latest(response,packageName,installed,System.currentTimeMillis()/1000L);
    }

    static UpdateRelease latest(JSONObject response, String packageName, long installed, long nowEpochSeconds) throws IOException {
        JSONArray releases=response.optJSONArray("releases");
        if(releases==null) throw new IOException("更新服务返回格式不正确");
        UpdateRelease best=null;
        for(int i=0;i<releases.length();i++) {
            JSONObject item=releases.optJSONObject(i);
            if(item==null || !packageName.equals(item.optString("bundle_id"))) continue;
            String version=field(item,"versionCode");
            if(version.isEmpty()) version=item.optString("build_version");
            long code;
            try { code=Long.parseLong(version); } catch(NumberFormatException e) { continue; }
            if(code<=installed || (best!=null && code<=best.code)) continue;
            String url=item.optString("install_url"), hash=field(item,"SHA256");
            if(!safeUrl(url)) throw new IOException("更新下载地址不受信任");
            if(!hash.matches("(?i)[a-f0-9]{64}")) throw new IOException("更新包缺少 SHA-256 校验信息，请联系发布者");
            long size=item.optLong("size",0);
            if(size<=0 || size>MAX_APK_BYTES) throw new IOException("更新包大小不正确");
            String name=item.optString("release_version",field(item,"versionName"));
            if(name.isEmpty()) name=String.valueOf(code);
            String policySchema=field(item,"updatePolicySchema");
            if(!policySchema.isEmpty()&&!"1".equals(policySchema))throw new IOException("不支持此更新策略版本");
            long minimumSupported=policyCode(item,"minimumSupportedVersionCode",code);
            long minimumRecommended=policyCode(item,"minimumRecommendedVersionCode",code);
            if(minimumSupported>0 && minimumRecommended>0 && minimumSupported>minimumRecommended)
                throw new IOException("更新策略中的最低支持版本不能高于最低推荐版本");
            long forceAfter=policyTime(item,"forceAfter");
            Mode mode;
            if(minimumSupported>0 && installed<minimumSupported && (forceAfter==0 || nowEpochSeconds>=forceAfter)) mode=Mode.REQUIRED;
            else if((minimumRecommended>0 && installed<minimumRecommended) || (minimumSupported>0 && installed<minimumSupported)) mode=Mode.RECOMMENDED;
            else mode=Mode.NORMAL;
            String message=mode==Mode.REQUIRED?field(item,"requiredUpdateMessage"):mode==Mode.RECOMMENDED?field(item,"recommendedUpdateMessage"):"";
            if(message.length()>500)throw new IOException("更新策略提示过长");
            best=new UpdateRelease(code,size,name,url,hash,item.optString("text_changelog","修复问题并改善使用体验。"),mode,message);
        }
        return best;
    }

    private static long policyCode(JSONObject item,String name,long releaseCode)throws IOException {
        String value=field(item,name);
        if(value.isEmpty())return 0;
        try {
            long code=Long.parseLong(value);
            if(code<0 || code>releaseCode)throw new NumberFormatException();
            return code;
        }catch(NumberFormatException e){throw new IOException("更新策略中的 "+name+" 不正确");}
    }

    private static long policyTime(JSONObject item,String name)throws IOException {
        String value=field(item,name);
        if(value.isEmpty())return 0;
        try{return OffsetDateTime.parse(value).toInstant().getEpochSecond();}
        catch(Exception e){throw new IOException("更新策略中的 "+name+" 不正确");}
    }

    static boolean safeUrl(String value) {
        try {
            URI uri=new URI(value);
            URI allowed=new URI(ORIGIN);
            return "https".equalsIgnoreCase(uri.getScheme()) && "https".equalsIgnoreCase(allowed.getScheme())
                && allowed.getHost()!=null && allowed.getHost().equalsIgnoreCase(uri.getHost())
                && (uri.getPort()==-1 || uri.getPort()==443) && uri.getUserInfo()==null
                && allowed.getPort()==-1 && allowed.getUserInfo()==null;
        } catch(Exception e) { return false; }
    }
}
