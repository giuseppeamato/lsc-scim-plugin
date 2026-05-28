//
// Questo file è stato generato dall'architettura JavaTM per XML Binding (JAXB) Reference Implementation, v2.2.8-b130911.1802
// Vedere <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a>
// Qualsiasi modifica a questo file andrà persa durante la ricompilazione dello schema di origine.
//


package it.pz8.lsc.plugins.connectors.scim.generated;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Classe Java per flatMultivalueStrategyType.
 *
 * <p>Il seguente frammento di schema specifica il contenuto previsto contenuto in questa classe.
 *
 * <pre>
 * &lt;simpleType name="flatMultivalueStrategyType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="ELEMENT_DIFF"/>
 *     &lt;enumeration value="WHOLESALE_REPLACE"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 *
 */
@XmlType(name = "flatMultivalueStrategyType")
@XmlEnum
public enum FlatMultivalueStrategyType {

    ELEMENT_DIFF,
    WHOLESALE_REPLACE;

    public String value() {
        return name();
    }

    public static FlatMultivalueStrategyType fromValue(String v) {
        return valueOf(v);
    }

}
