package com.xx.hikvisiondemo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

public class NotNull {

	
	public static boolean isNotNull(Integer i) {
		if (null != i && 0 != i) {
			return true;
		}
		return false;
	}

	public static boolean isNotNull(Double d) {
		if (null != d && 0 != d) {
			return true;
		}
		return false;
	}

	public static boolean isNotNull(Object object) {
		if (null != object && !"".equals(object)) {
			return true;
		}
		return false;
	}

	public static boolean isNotNull(List<?> t) {
		if (null != t && t.size() > 0) {
			return true;
		}
		return false;
	}

	public static boolean isNotNull(Map<?, ?> t) {
		if (null != t && t.size() > 0) {
			return true;
		}
		return false;
	}

	public static boolean isNotNull(Object[] objects) {
		if (null != objects && objects.length > 0) {
			return true;
		}
		return false;
	}

	public static boolean isNotNull(JSONArray jsonArray) {
		if (null != jsonArray && jsonArray.length() > 0) {
			return true;
		}
		return false;
	}

	public static boolean isNotNull(JSONObject jsonObject) {
		if (null != jsonObject && !"".equals(jsonObject)) {
			return true;
		}
		return false;
	}

	public static boolean isNotNullAndNaN(Object object) {
		if (isNotNull(object) && !object.toString().equals("NaN")) {
			return true;
		}
		return false;
	}

}
