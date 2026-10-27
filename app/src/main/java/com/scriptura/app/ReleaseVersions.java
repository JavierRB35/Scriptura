package com.scriptura.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Versiones 0.x y compilaciones de GitHub Actions; sin dependencias Android. */
public final class ReleaseVersions {
    private static final Pattern TAG=Pattern.compile(
        "^[vV]?(\\d{1,4})\\.(\\d{1,4})\\.(\\d{1,4})(?:-build\\.(\\d{1,10}))?$");
    public final int major,minor,patch;
    public final long build;

    private ReleaseVersions(int major,int minor,int patch,long build){
        this.major=major;this.minor=minor;this.patch=patch;this.build=build;
    }
    public static ReleaseVersions parse(String raw){
        if(raw==null)return null;
        Matcher m=TAG.matcher(raw.trim());
        if(!m.matches())return null;
        try{
            int major=Integer.parseInt(m.group(1));
            int minor=Integer.parseInt(m.group(2));
            int patch=Integer.parseInt(m.group(3));
            long build=m.group(4)==null?0:Long.parseLong(m.group(4));
            if(build>Integer.MAX_VALUE)return null;
            return new ReleaseVersions(major,minor,patch,build);
        }catch(NumberFormatException err){return null;}
    }
    public int compareTo(ReleaseVersions other){
        if(major!=other.major)return Integer.compare(major,other.major);
        if(minor!=other.minor)return Integer.compare(minor,other.minor);
        if(patch!=other.patch)return Integer.compare(patch,other.patch);
        return Long.compare(build,other.build);
    }
    public static boolean isNewer(String remote,String installed){
        ReleaseVersions r=parse(remote),i=parse(installed);
        return r!=null&&i!=null&&r.compareTo(i)>0;
    }
}
