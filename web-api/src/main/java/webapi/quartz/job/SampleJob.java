package webapi.quartz.job;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import lombok.extern.slf4j.Slf4j;

/**
 * 示例不带参定时任务
 *
 * @Author Scott
 */
@Slf4j
public class SampleJob implements Job {



	@Override
	public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {

	}
}
