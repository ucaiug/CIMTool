<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet 
    version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema" 
    xmlns:mdimsg="mdimsg"
    xmlns:a="http://langdale.com.au/2005/Message#"
>
           
    <xsl:output indent="yes"/>
    <xsl:param name="version"></xsl:param>
    <xsl:param name="name"></xsl:param>
    <xsl:param name="baseURI"></xsl:param>
    
    <xsl:template match="a:Message">
        <!--  the top level template generates the xml schema element for the message -->
        <xs:schema
            targetNamespace="{@name}" 
            elementFormDefault="qualified" 
            attributeFormDefault="unqualified">

            <!-- copy through namespace declaration needed to reference local types -->
            <xsl:for-each select="namespace::*">           
                <xsl:copy/>
            </xsl:for-each>
            
            <xs:annotation>
        	<xs:documentation>Generated by CIMTool <xsl:value-of select="$version"/>, see https://cimtool.ucaiug.io</xs:documentation>
            </xs:annotation>
 
            <xs:import namespace="mdimsg" schemaLocation="mdiMessage.xsd"/>
            
            <xs:element name="{@name}">
		<xs:complexType>
                    <xsl:apply-templates mode="annotate"/>
			<xs:sequence>
				<xs:element name="MessageHeader" type="mdimsg:MessageHeader" minOccurs="0"/>
				<xs:element name="MessagePayload">
					<xs:complexType>
						<xs:sequence>                 
                                                    <xsl:apply-templates/>
                                                </xs:sequence>
                                        </xs:complexType>
                                 </xs:element>
                        </xs:sequence>
                </xs:complexType>
            </xs:element>
            
            <xsl:apply-templates mode="declare"/>
	
        </xs:schema>
    </xsl:template>

    <xsl:template match="a:Root">
        <!--  generates the root payload element definitions -->
        <xs:element name="{@name}" minOccurs="0" maxOccurs="unbounded">
          <xs:complexType>
              <xsl:apply-templates mode="annotate"/>
              <xs:sequence>
                <xsl:apply-templates/>
              </xs:sequence>
          </xs:complexType>
        </xs:element>
        
    </xsl:template>

    <xsl:template match="a:Complex">
        <!--  generates the nested element definitions -->
        <xs:element name="{@name}" minOccurs="{@minOccurs}" maxOccurs="{@maxOccurs}">
          <xs:complexType>
              <xsl:apply-templates mode="annotate"/>
              <xs:sequence>
                <xsl:apply-templates/>
              </xs:sequence>
          </xs:complexType>
        </xs:element>
        
    </xsl:template>

    <xsl:template match="a:Domain">
        <!--  generates a simple element for a domain type -->
        <xs:element name="{@name}" minOccurs="{@minOccurs}" maxOccurs="{@maxOccurs}" type="m:{@type}">
            <xsl:apply-templates mode="annotate"/>
        </xs:element>
    </xsl:template>

    <xsl:template match="a:Simple">
        <!--  generates a simple element for a domain type -->
        <xs:element name="{@name}" minOccurs="{@minOccurs}" maxOccurs="{@maxOccurs}" type="xs:{@xstype}">
            <xsl:apply-templates mode="annotate"/>
        </xs:element>
    </xsl:template>
 
    <xsl:template match="a:SimpleType"  mode="declare">
        <!--  generates a simple element for a domain type -->
        <xs:simpleType name="{@name}">
            <xsl:apply-templates mode="annotate"/>
            <xs:restriction  base="xs:{@xstype}"/>
        </xs:simpleType>
    </xsl:template>
    
    <xsl:template match="a:Reference">
        <!-- generates a reference to an object in the model -->
        <xs:element name="{@name}" minOccurs="{@minOccurs}" maxOccurs="{@maxOccurs}" type="mdimsg:SimpleAssociation">
                <xsl:apply-templates mode="annotate"/>
        </xs:element>
    </xsl:template>
    
    <xsl:template match="a:Enumerated">
        <!-- generates a reference to an object in the model -->
        <xs:element name="{@name}" minOccurs="{@minOccurs}" maxOccurs="{@maxOccurs}" type="m:{@type}">
                <xsl:apply-templates mode="annotate"/>
        </xs:element>
    </xsl:template>
    
    <xsl:template match="a:EnumeratedType"  mode="declare">
        <!-- generates a reference to an object in the model -->
        <xs:simpleType name="{@name}" >
                <xsl:apply-templates mode="annotate"/>
                <xs:restriction base="xs:string"> 
                    <xsl:apply-templates  mode="declare"/>
                </xs:restriction>
        </xs:simpleType>
    </xsl:template>
    
    
    <xsl:template match="a:EnumeratedValue"  mode="declare">
        <!-- generates a reference to an object in the model -->
        <xs:enumeration value="{@name}" >
                <xsl:apply-templates mode="annotate"/>
        </xs:enumeration>
    </xsl:template>
    
    
    <xsl:template match="a:Comment" mode="annotate">
        <!--  generate and annotation -->
        <xs:annotation>
	   <xs:documentation>
	       <xsl:value-of select="."/>
            </xs:documentation>
	</xs:annotation>
    </xsl:template>
    
    <xsl:template match="text()">
      <!--  dont pass text through  -->
    </xsl:template>
    
    <xsl:template match="node()" mode="annotate">
      <!-- dont pass any defaults in annotate mode -->
    </xsl:template>
    
    <xsl:template match="node()" mode="declare">
      <!-- dont pass any defaults in annotate mode -->
    </xsl:template>

</xsl:stylesheet>