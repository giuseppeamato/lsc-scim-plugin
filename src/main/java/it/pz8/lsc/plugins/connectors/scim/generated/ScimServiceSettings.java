//
// Questo file è stato generato dall'architettura JavaTM per XML Binding (JAXB) Reference Implementation, v2.2.8-b130911.1802 
// Vedere <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Qualsiasi modifica a questo file andrà persa durante la ricompilazione dello schema di origine. 
// Generato il: 2020.07.07 alle 05:40:53 PM CEST 
//


package it.pz8.lsc.plugins.connectors.scim.generated;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import org.lsc.configuration.ServiceType;


/**
 * <p>Classe Java per anonymous complex type.
 * 
 * <p>Il seguente frammento di schema specifica il contenuto previsto contenuto in questa classe.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;extension base="{http://lsc-project.org/XSD/lsc-core-2.1.xsd}serviceType">
 *       &lt;sequence>
 *         &lt;element name="entity" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="pivot" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="domain" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="pageSize" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *         &lt;element name="filter" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="attributes" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="excludedAttributes" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
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
    "domain",
    "pageSize",
    "filter",
    "attributes",
    "excludedAttributes"
})
@XmlRootElement(name = "scimServiceSettings")
public class ScimServiceSettings
    extends ServiceType
{

    @XmlElement(required = true)
    protected String entity;
    protected String pivot;
    protected String domain;
    protected Integer pageSize;
    protected String filter;
    protected String attributes;
    protected String excludedAttributes;

    /**
     * Recupera il valore della propriet� entity.
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
     * Imposta il valore della propriet� entity.
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
     * Recupera il valore della propriet� pivot.
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
     * Imposta il valore della propriet� pivot.
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
     * Recupera il valore della propriet� domain.
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
     * Imposta il valore della propriet� domain.
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
     * Recupera il valore della propriet� pageSize.
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
     * Imposta il valore della propriet� pageSize.
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
     * Recupera il valore della propriet� filter.
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
     * Imposta il valore della propriet� filter.
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
     * Recupera il valore della propriet� attributes.
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
     * Imposta il valore della propriet� attributes.
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
     * Recupera il valore della propriet� excludedAttributes.
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
     * Imposta il valore della propriet� excludedAttributes.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setExcludedAttributes(String value) {
        this.excludedAttributes = value;
    }

}
