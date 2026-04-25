package it.pz8.lsc.plugins.connectors.scim.bean;

/**
 * @author Giuseppe Amato
 *
 */
public final class CachedData {

	private String pivot;
	private String sourceUUID;
	private String scimId;
	private String entity;
	
	public CachedData(String pivot, String sourceUUID, String scimId, String entity) {
		super();
		this.pivot = pivot;
		this.sourceUUID = sourceUUID;
		this.scimId = scimId;
		this.entity = entity;
	}

	public String getPivot() {
		return pivot;
	}

	public String getSourceUUID() {
		return sourceUUID;
	}

	public String getScimId() {
		return scimId;
	}

	public String getEntity() {
		return entity;
	}
}
