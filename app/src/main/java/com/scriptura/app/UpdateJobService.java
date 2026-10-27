package com.scriptura.app;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Comprobación periódica con red y sin actividad visible. */
public final class UpdateJobService extends JobService {
    private volatile Thread running;
    @Override public boolean onStartJob(JobParameters params){
        if(!UpdateChecker.configured(this))return false;
        running=new Thread(()->{
            UpdateChecker.Result result=UpdateChecker.check(getApplicationContext(),true);
            if(result.available())UpdateChecker.maybeNotify(getApplicationContext(),result.update);
            jobFinished(params,false);
        },"scriptura-update-check");
        running.start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params){
        Thread work=running;
        if(work!=null)work.interrupt();
        return true;
    }
}
