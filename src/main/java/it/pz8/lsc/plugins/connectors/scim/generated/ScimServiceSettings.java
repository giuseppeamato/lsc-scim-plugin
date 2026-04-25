//
// Questo fileè stato generato dall'architettura JavaTM per XML Binding (JAXB) Reference Implementation, v2.2.8-b130911.1802 
// Vedere <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Qualsiasi modifica a questo file andrà persa durante la ricompilazione dello schema di origine. 
// Generato il: 2026.04.15 alle 10:04:25 PM CEST 
//


package it.pz8.lsc.plugins.connectors.scim.generated;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import org.lsc.configuration.ServiceType;
import org.lsc.configuration.ValuesType;


/**
 * <p>Classe Java per anonymous complex type.
 * 
 * <p>Il seguente frammento di schema specifica il contenuto previsto contenuto in questa classe.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;extension base="{http://lsc-project.org/XSD/lsc-core-2.2.xsd}serviceType">
 *       &lt;sequence>
 *         &lt;element name="entity" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="pivot" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="sourcePivot" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="sourceUUID" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="cacheConnection" minOccurs="0">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;attribute name="reference" use="required" type="{http://www.w3.org/2001/XMLSchema}IDREF" />
 *                 &lt;attribute name="writeEnabled" use="required" type="{http://www.w3.org/2001/XMLSchema}boolean" />
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *         &lt;element name="domain" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="pageSize" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *         &lt;element name="filter" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="attributes" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="excludedAttributes" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="schema" type="{http://lsc-project.org/XSD/lsc-scim-plugin-1.2.xsd}schemasType" minOccurs="0"/>
 *         &lt;sequence>
 *           &lt;element name="writableAttributes" type="{http://lsc-project.org/XSD/lsc-core-2.2.xsd}valuesType" minOccurs="0"/>
 *         &lt;/sequence>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "entity",
    "pivot",
    "sourcePivot",
    "sourceUUID",
    "cacheConnection",
    "domain",
    "pageSize",
    "filter",
    "attributes",
    "excludedAttributes",
    "schema",
    "writableAttributes"
})
@XmlRootElement(name = "scimServiceSettings")
public class ScimServiceSettings
    extends ServiceType
{

    @XmlElement(required = true)
    protected String entity;
    protected String pivot;
    protected String sourcePivot;
    protected String sourceUUID;
    protected ScimServiceSettings.CacheConnection cacheConnection;
    protected String domain;
    protected Integer pageSize;
    protected String filter;
    protected String attributes;
    protected String excludedAttributes;
    protected SchemasType schema;
    protected ValuesType writableAttributes;

    /**
     * Recupera il valore della proprietà entity.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getEntity() {
        return entity;
    }

    /**
     * Imposta il valore della proprietà entity.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setEntity(String value) {
        this.entity = value;
    }

    /**
     * Recupera il valore della proprietà pivot.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPivot() {
        return pivot;
    }

    /**
     * Imposta il valore della proprietà pivot.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPivot(String value) {
        this.pivot = value;
    }

    /**
     * Recupera il valore della proprietà sourcePivot.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSourcePivot() {
        return sourcePivot;
    }

    /**
     * Imposta il valore della proprietà sourcePivot.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSourcePivot(String value) {
        this.sourcePivot = value;
    }

    /**
     * Recupera il valore della proprietà sourceUUID.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSourceUUID() {
        return sourceUUID;
    }

    /**
     * Imposta il valore della proprietà sourceUUID.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSourceUUID(String value) {
        this.sourceUUID = value;
    }

    /**
     * Recupera il valore della proprietà cacheConnection.
     * 
     * @return
     *     possible object is
     *     {@link ScimServiceSettings.CacheConnection }
     *     
     */
    public ScimServiceSettings.CacheConnection getCacheConnection() {
        return cacheConnection;
    }

    /**
     * Imposta il valore della proprietà cacheConnection.
     * 
     * @param value
     *     allowed object is
     *     {@link ScimServiceSettings.CacheConnection }
     *     
     */
    public void setCacheConnection(ScimServiceSettings.CacheConnection value) {
        this.cacheConnection = value;
    }

    /**
     * Recupera il valore della proprietà domain.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDomain() {
        return domain;
    }

    /**
     * Imposta il valore della proprietà domain.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDomain(String value) {
        this.domain = value;
    }

    /**
     * Recupera il valore della proprietà pageSize.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getPageSize() {
        return pageSize;
    }

    /**
     * Imposta il valore della proprietà pageSize.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setPageSize(Integer value) {
        this.pageSize = value;
    }

    /**
     * Recupera il valore della proprietà filter.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFilter() {
        return filter;
    }

    /**
     * Imposta il valore della proprietà filter.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFilter(String value) {
        this.filter = value;
    }

    /**
     * Recupera il valore della proprietà attributes.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAttributes() {
        return attributes;
    }

    /**
     * Imposta il valore della proprietà attributes.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAttributes(String value) {
        this.attributes = value;
    }

    /**
     * Recupera il valore della proprietà excludedAttributes.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getExcludedAttributes() {
        return excludedAttributes;
    }

    /**
     * Imposta il valore della proprietà excludedAttributes.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setExcludedAttributes(String value) {
        this.excludedAttributes = value;
    }

    /**
     * Recupera il valore della proprietà schema.
     * 
     * @return
     *     possible object is
     *     {@link SchemasType }
     *     
     */
    public SchemasType getSchema() {
        return schema;
    }

    /**
     * Imposta il valore della proprietà schema.
     * 
     * @param value
     *     allowed object is
     *     {@link SchemasType }
     *     
     */
    public void setSchema(SchemasType value) {
        this.schema = value;
    }

    /**
     * Recupera il valore della proprietà writableAttributes.
     * 
     * @return
     *     possible object is
     *     {@link ValuesType }
     *     
     */
    public ValuesType getWritableAttributes() {
        return writableAttributes;
    }

    /**
     * Imposta il valore della proprietà writableAttributes.
     * 
     * @param value
     *     allowed object is
     *     {@link ValuesType }
     *     
     */
    public void setWritableAttributes(ValuesType value) {
        this.writableAttributes = value;
    }


    /**
     * <p>Classe Java per anonymous complex type.
     * 
     * <p>Il seguente frammento di schema specifica il contenuto previsto contenuto in questa classe.
     * 
     * <pre>
     * &lt;complexType>
     *   &lt;complexContent>
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *       &lt;attribute name="reference" use="required" type="{http://www.w3.org/2001/XMLSchema}IDREF" />
     *       &lt;attribute name="writeEnabled" use="required" type="{http://www.w3.org/2001/XMLSchema}boolean" />
     *     &lt;/restriction>
     *   &lt;/complexContent>
     * &lt;/complexType>
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "")
    public static class CacheConnection {

        @XmlAttribute(name = "reference", required = true)
        @XmlIDREF
        @XmlSchemaType(name = "IDREF")
        protected Object reference;
        @XmlAttribute(name = "writeEnabled", required = true)
        protected boolean writeEnabled;

        /**
         * Recupera il valore della proprietà reference.
         * 
         * @return
         *     possible object is
         *     {@link Object }
         *     
         */
        public Object getReference() {
            return reference;
        }

        /**
         * Imposta il valore della proprietà reference.
         * 
         * @param value
         *     allowed object is
         *     {@link Object }
         *     
         */
        public void setReference(Object value) {
            this.reference = value;
        }

        /**
         * Recupera il valore della proprietà writeEnabled.
         * 
         */
        public boolean isWriteEnabled() {
            return writeEnabled;
        }

        /**
         * Imposta il valore della proprietà writeEnabled.
         * 
         */
        public void setWriteEnabled(boolean value) {
            this.writeEnabled = value;
        }

    }

}
