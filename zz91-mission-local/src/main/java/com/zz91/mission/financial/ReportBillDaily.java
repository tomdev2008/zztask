package com.zz91.mission.financial;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.zz91.task.common.ZZTask;
import com.zz91.util.datetime.DateUtil;
import com.zz91.util.db.DBUtils;
import com.zz91.util.db.IReadDataHandler;
import com.zz91.util.db.pool.DBPoolFactory;
/**
 * 获取科目A
 * 得到该科目上一期期末
 * 获取本期该科目凭证
 * 统计借贷和期末
 * 完成科目A的统计工作
 * 循环完成所有科目统计
 * */
public class ReportBillDaily implements ZZTask {

	final static String DB = "financial";

	final static String DATE_FORMAT = "yyyy-MM-dd 00:00:00";
	
	final static String COA_PREFIX="";
	
	final static int PAGE_SIZE=50;
	
	final Map<String, Long> FREEZE=new HashMap<String, Long>();
	
	@Override
	public boolean init() throws Exception {
		
		
		
		return false;
	}
	
	@Override
	public boolean exec(Date baseDate) throws Exception {
		//获取科目A
		//得到该科目上一期期末
		//获取本期该科目凭证
		//统计借贷和期末
		//完成科目A的统计工作
		//循环完成所有科目统计
		
		Date periodTo= DateUtil.getDate(baseDate, DATE_FORMAT);
		Date periodFrom=DateUtil.getDateAfterDays(periodTo, -1);
		
		Integer start=0;
		Set<String> coaSet = new HashSet<String>();
		do {
			coaSet = queryCoa(start);

			analysis(coaSet, periodFrom, periodTo);
			
			start=start+PAGE_SIZE;
		} while (coaSet.size()>0);
		
		return true;
	}
	
	private Set<String> queryCoa(Integer start){
		final Set<String> coaset=new HashSet<String>();
		
		String sql="select code_coa from config_coa where isleaf=1 and islock=0 limit "+start+","+PAGE_SIZE;
		DBUtils.select(DB, sql, new IReadDataHandler() {
			
			@Override
			public void handleRead(ResultSet rs) throws SQLException {
				while (rs.next()) {
					coaset.add(rs.getString(1));
				}
			}
		});
		
		return coaset;
	}

	private void analysis(Set<String> coaSet, Date from, Date to){
		
		for(String coa: coaSet){
			if(coa.length()!=7){
				continue ;
			}
			//from:is this period's from, is last peroid's to
//			Integer beginingBalance=queryEndBalanaceFromLastPeroid(coa, from);
//			if(beginingBalance==null){
//				beginingBalance=0;
//			}
			//key: dr,cr
			Map<String, Integer> sumMap=querySumCy(coa, from, to);
			if(sumMap==null || sumMap.size()<2){
				continue ;
			}
			if(sumMap.get("cr")==null){
				sumMap.put("cr", 0);
			}
			if(sumMap.get("dr")==null){
				sumMap.put("dr", 0);
			}
			
			Integer beginingBalance = queryEndBalanace(coa, from);
			Integer endBalance=queryEndBalanace(coa, to);
			saveAnalysis(coa, beginingBalance, endBalance, sumMap.get("dr"), sumMap.get("cr"), from);
		}
	}
	
	private void saveAnalysis(String coa, Integer bb, Integer eb, Integer sumdr, Integer sumcr, Date from){
		
		//要么从缓存中获取，要么从昨天获取
		if(!FREEZE.containsKey(coa)){
			FREEZE.put(coa, queryFreeze(coa, from));
		}
		
		String dept=coa.substring(3, 5);
		StringBuffer sb=new StringBuffer();
		sb.append(" insert into report_bill(code_coa, code_item_dept, ")
				.append("cy_begining_balance, cy_end_balance, cy_dr_sum, ")
				.append("cy_cr_sum, report_category, gmt_report, ")
				.append("gmt_created, gmt_modified, cy_freeze) ")
				.append(" values('").append(coa).append("','")
				.append(dept).append("',")
				.append(bb).append(",")
				.append(eb).append(",")
				.append(sumdr).append(",")
				.append(sumcr).append(",0,'")
				.append(DateUtil.toString(from, DATE_FORMAT))
				.append("',now(),now(),")
				.append(FREEZE.get(coa)).append(" )");
		
		DBUtils.insertUpdate(DB, sb.toString());
		
	}
	
	private Long queryFreeze(String coa, Date from){
		
		final Long[] cyfreeze={0l};
		
		DBUtils.select(DB, "select cy_freeze from report_bill where code_coa='"+coa+"' and gmt_report<'"+DateUtil.toString(from, DATE_FORMAT)+"' and report_category=0", new IReadDataHandler() {
			
			@Override
			public void handleRead(ResultSet rs) throws SQLException {
				while( rs.next() ){
					cyfreeze[0] = rs.getLong(1);
				}
			}
		});
		
		if(cyfreeze.length>0){
			return cyfreeze[0];
		}
		return 0l;
	}
	
	private Map<String, Integer> querySumCy(String coa, Date from, Date to){
		StringBuffer sb=new StringBuffer();
		sb.append("select sum(cy_dr) as dr, sum(cy_cr) as cr from ac_document where ")
			.append(" gmt_build >='")
			.append(DateUtil.toString(from, DATE_FORMAT))
			.append("' and gmt_build <'")
			.append(DateUtil.toString(to, DATE_FORMAT))
			.append("' and code_coa='").append(coa).append("' ");
		
		final Map<String, Integer> map=new HashMap<String, Integer>();
		DBUtils.select(DB, sb.toString(), new IReadDataHandler() {
			
			@Override
			public void handleRead(ResultSet rs) throws SQLException {
				while (rs.next()) {
					map.put("dr", rs.getInt(1));
					map.put("cr", rs.getInt(2));
				}
			}
		});
		
		return map;
	}
	
	private Integer queryEndBalanace(String coa, Date peroid){

		final Integer[] result={0};
		
		String sql="select abb.cy_balance from ac_bill_book abb where abb.code_coa='"+coa
				+"' and abb.gmt_build <'"
				+DateUtil.toString(peroid, DATE_FORMAT)
				+"' order by abb.gmt_build desc, id desc limit 1";
		
		//System.out.println(sql);;
		DBUtils.select(DB, sql, new IReadDataHandler() {
			
			@Override
			public void handleRead(ResultSet rs) throws SQLException {
				while(rs.next()){
					result[0]=rs.getInt(1);
				}
			}
			
		});
		
		if(result.length>0){
			return result[0];
		}else{
			return 0;
		}
	}
	
	@Override
	public boolean clear(Date baseDate) throws Exception {
		Date from=DateUtil.getDateAfterDays(baseDate, -1);
		
		//获取冻结资金
		
		DBUtils.select(DB, "select code_coa, cy_freeze from report_bill where gmt_report='"+DateUtil.toString(from, DATE_FORMAT)+"' and report_category=0 and cy_freeze>0", new IReadDataHandler() {
			
			@Override
			public void handleRead(ResultSet rs) throws SQLException {
				while (rs.next()) {
					FREEZE.put(rs.getString(1), rs.getLong(2));
				}
			}
			
		});
		
		
		DBUtils.insertUpdate(DB, "delete from report_bill where gmt_report='"+DateUtil.toString(from, DATE_FORMAT)+"' and report_category=0");
		return true;
	}
	
	public static void main(String[] args) {
		DBPoolFactory.getInstance().init("file:/usr/tools/config/db/db-zztask-jdbc.properties");
		
		ReportBillDaily task=new ReportBillDaily();
		
		try {
			
			task.clear(DateUtil.getDate("2013-04-07 00:00:00", "yyyy-MM-dd HH:mm:ss"));
			task.exec(DateUtil.getDate("2013-04-07 00:00:00", "yyyy-MM-dd HH:mm:ss"));
			
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

}
