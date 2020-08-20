package it.pz8.lsc.plugins.connectors.scim.bean;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * @author Giuseppe Amato
 *
 */
public class ScimPathOperation extends ScimOperation implements Serializable {

	private static final long serialVersionUID = -6350260898633591836L;
	
	private String path;
	private Object value;
	
	public ScimPathOperation(String op, String path)  {
		this.op = op;
		this.path = path;
	}
	
	public ScimPathOperation(String op, String path, Object value)  {
		this.op = op;
		this.path = path;
		this.value = value;
	}
	
	public String getPath() {
		return path;
	}

	@JsonInclude(Include.NON_NULL)
	public Object getValue() {
		return value;
	}

}
