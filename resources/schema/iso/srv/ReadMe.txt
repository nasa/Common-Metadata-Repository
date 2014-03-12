
Validated with XSV 2.10, Xerces J 2.7.1 and XML Spy 2009 (2009-03-02, IGN / France - Nicolas Lesage / Marcellin Prudham)


**************************

Package srv from Eden repository (http://eden.ign.fr/xsd/isotc211/iso19119/20071126) modified as follows :

- serviceMetadata.xsd line75/76:
<xs:element name="couplingType" type="srv:SV_CouplingType_PropertyType"/>
<xs:element name="coupledResource" type="srv:SV_CoupledResource_PropertyType" minOccurs="0" maxOccurs="unbounded"/>
REPLACED BY:
<xs:element name="coupledResource" type="srv:SV_CoupledResource_PropertyType" minOccurs="0" maxOccurs="unbounded"/>
<xs:element name="couplingType" type="srv:SV_CouplingType_PropertyType"/>

- serviceMetadata.xsd line141:
<xs:sequence>
	<xs:element name="operationName" type="gco:CharacterString_PropertyType"/>
	<xs:element name="identifier" type="gco:CharacterString_PropertyType"/>
</xs:sequence>
REPLACED BY:
<xs:sequence>
	<xs:element name="operationName" type="gco:CharacterString_PropertyType"/>
	<xs:element name="identifier" type="gco:CharacterString_PropertyType"/>
	<xs:element ref="gco:ScopedName" minOccurs="0"/>
</xs:sequence>