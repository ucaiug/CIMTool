/*
 * This software is Copyright 2005,2006,2007,2008 Langdale Consultants.
 * Langdale Consultants can be contacted at: http://www.langdale.com.au
 */
package au.com.langdale.profiles;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import au.com.langdale.jena.Models;
import au.com.langdale.jena.OntSubject;
import au.com.langdale.profiles.ProfileClass.PropertyInfo;
import au.com.langdale.xmi.UML;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.ontology.OntProperty;
import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.XSD;

/**
 * This class is the driver for a number of profile model transformation and 
 * conversion utilities.  It traverses a profile model and fires events (defined 
 * by abstract methods) for each feature of the profile.
 * 
 * TODO: this class has not been updated to handle OWL unions in the profile.
 */
public abstract class SchemaGenerator extends ProfileUtility implements Runnable {
	private OntModel model;
	private OntModel profileModel;
	private String namespace;
	private Catalog catalog;
	private Set datatypes = new HashSet();
	private Set packages = new HashSet();
	private PropertyAccumulator props = new PropertyAccumulator();
	private EnumAccumulator enums = new EnumAccumulator();
	
	private List work = new LinkedList(); // unprocessed profiles
	
	public class Catalog extends BaseMap {
		protected Map classes = new HashMap(); 	// base class to profile uri 
		private Map profiles = new HashMap(); 	// profile uri to base class
		
		public Catalog() {
		}

		@Override
		public void add(OntClass base, OntClass clss) {
			
			if(clss.isAnon()) {
				if( ! classes.containsKey(base)) {
					add(base, constructURI(base));
				}
			}
			else {
				if( classes.containsKey(base) )
					rename(base);
				else
					add( base, constructURI(base, clss));
			}
			super.add(base, clss); // purely to support findProfiles()
		}

		public void add(OntClass base, String uri) {
			Object alias = profiles.get(uri);
			if( alias != null ){
				if(alias.equals(base)) 
					return;
				rename((OntClass)alias);
			}
			classes.put(base, uri);
			profiles.put(uri, base);
		}
		
		public boolean add(OntClass base) {
			if( classes.containsKey(base)) 
				return false;

			add(base, constructURI(base));
			return true;
		}
		
		private void rename(OntClass base) {
			String uri = constructURI(base);
			String old = (String) classes.get(base);
			if( old == null || uri.equals(old))
				return;
			classes.remove(base);
			profiles.remove(old);
			OntClass alias = (OntClass) profiles.get(uri);
			if( alias != null )
				rename(alias);
			classes.put(base, uri);
			profiles.put(uri, base);
		}

		public String getURI(OntResource base) {
			return (String) classes.get(base);
		}
		
		public Collection getBases() {
			return classes.keySet();
		}
		
		public Collection getURIs() {
			return classes.values();
		}

		public OntModel buildLattice() {
			OntModel hierarchy = ModelFactory.createOntologyModel(OntModelSpec.RDFS_MEM_TRANS_INF);
			Collection bases = getBases();
			Iterator it = bases.iterator();
			while(it.hasNext()) {
				OntClass clss = (OntClass)it.next();
				OntClass profile = hierarchy.createClass(getURI(clss));
				
				Iterator jt = new OntSubject(clss).listSuperClasses(false);
				while(jt.hasNext()) {
					OntResource superClass = (OntResource) jt.next();
					if( bases.contains(superClass))
						profile.addSuperClass(hierarchy.createClass(getURI(superClass)));
				}
			}
			return hierarchy;
		}
	}

	public static class TypeInfo {
		public final String type, xsdtype;
		public TypeInfo(OntResource range, SchemaGenerator context) {
			if( range != null ) {
				if( range.getNameSpace().equals(XSD.getURI())) {
					type = null;
					xsdtype = range.getURI();
				}
				else {
					type = context.constructURI(range);
					Resource cand = range.getSameAs();
					if( cand != null && cand.getNameSpace().equals(XSD.getURI()))
						xsdtype = cand.getURI();
					else
						xsdtype = null;
				}
			}
			else {
				type = xsdtype = null;
			}
		}
	}

	public SchemaGenerator(OntModel profileModel, OntModel backgroundModel, String namespace) {
		this.profileModel = profileModel;
		this.model = Models.merge(profileModel, backgroundModel);
		this.namespace = namespace;
		this.catalog = new Catalog();
	}
	
	
	public void run() {
		
		scanProfiles();
		scanDomainsAndRanges();
		
		// emit classes first
		Iterator it = catalog.getBases().iterator();
		while( it.hasNext()) {
			OntClass base = (OntClass)it.next();
			generateClass(base);
		}
		
		// emit datatypes
		Iterator nt = datatypes.iterator();
		while( nt.hasNext()) {
			OntResource type = (OntResource)nt.next();
			TypeInfo info = new TypeInfo(type, this);
			if( info.type != null) {
				emitDatatype(info.type, info.xsdtype);
				annotate(info.type, type);
			}
		}
		
		// emit properties
		Iterator lt = props.getAll().iterator();
		while( lt.hasNext()) {
			generateProperty((PropertySpec)lt.next());
		}
		
		// emit superclass relationships
		generateLattice(catalog.buildLattice());
	}

	// construct a URI for a base model class, datatype, property or individual 
	private String constructURI(OntResource base) {
		if( namespace != null)
			return namespace + base.getLocalName();
		else
			return base.getURI();
	}

	// construct a URI for named profile
	private String constructURI(OntResource base, OntResource profile) {
		if( namespace != null)
			return profile.getURI(); // use the profile URI directly, usually in namespace but not always
		else
			return base.getURI();
	}

	private void scanProfiles() {
		Iterator it = ProfileClass.getProfileClasses(profileModel, model);
		while( it.hasNext()) 
			work.add(it.next());
		
		while( ! work.isEmpty()) {
			ProfileClass profile = (ProfileClass) work.remove(0);
			scanProperties(profile);
			OntClass base = profile.getBaseClass();
			if( base == null) {
				log("No base for profile class", profile.getSubject());
			}
			else {
				catalog.add(base, profile.getSubject());
				if(profile.isEnumerated() || profile.isRestrictedEnum())
					enums.add(base, profile.getIndividuals());
			}
		}
	}

	private boolean scanProperties(ProfileClass profile) {
		Iterator it = profile.getProperties();
		boolean some = it.hasNext();
		
		while( it.hasNext()) {
			PropertyInfo info = profile.getPropertyInfo((OntProperty) it.next());
			ProfileClass range_profile = props.add( info );
			if( range_profile != null)
				work.add(range_profile);
		}
		
		return some;
	}

	private void scanDomainsAndRanges() {
		Iterator it = props.getAll().iterator();
		while( it.hasNext()) {
			PropertySpec spec = (PropertySpec) it.next();
			catalog.add(spec.base_domain);
			if( spec.base_range != null) {
				catalog.add(spec.base_range);
			}
			else  {
				OntResource range = spec.prop.getRange();
				if( range != null)
					datatypes.add(range);
			}
		}
	}

	private void generateLattice(OntModel hierarchy) {
		Collection uris = catalog.getURIs();
		Iterator it = uris.iterator();
		while(it.hasNext()) {
			OntClass profile = hierarchy.createOntResource((String)it.next()).asClass();
		
			Iterator jt = new OntSubject(profile).listSuperClasses(true);
			while(jt.hasNext()) {
				OntResource superClass = (OntResource) jt.next();
				emitSuperClass(profile.getURI(), superClass.getURI());
			}
		}
	}

	private void generateClass(OntClass base) {
		String uri = catalog.getURI(base);

		emitClass(uri, base.getURI());
		emitLabel(uri, ResourceFactory.createResource(uri).getLocalName());
		emitComment(uri, extractComment(base), extractProfileComment(base));
		generateIndividuals(uri, base);
		generateStereotypes(uri, base);
		generatePackage(uri, base);
	}

	private void generateIndividuals(String type_uri, OntClass base) {
		for (Iterator ix = enums.get(base).iterator(); ix.hasNext();) {
			OntResource instance = (OntResource) ix.next();
			String uri = constructURI(instance);
			emitInstance(uri, instance.getURI(), type_uri);
			annotate(uri, instance);
		}
	}

	private void generateProperty(PropertySpec info) {
		OntProperty prop = info.prop;
		String uri = constructURI(prop); 
		String domain = catalog.getURI(info.base_domain);
		if(prop.isDatatypeProperty()) {
			TypeInfo range = new TypeInfo( prop.getRange(), this);
			emitDatatypeProperty(uri, prop.getURI(), domain, range.type, range.xsdtype, info.required);
		}
		else {
			String range = catalog.getURI(info.base_range);
			emitObjectProperty(uri, prop.getURI(), domain, range, info.required, info.functional);
			
			OntProperty inverse = prop.getInverse();
			if( inverse != null && props.containsKey(inverse)) {
				emitInverse(uri, constructURI(inverse));
			}
		}
		if( info.label != null)
			emitLabel(uri, info.label);
		
		emitComment(uri, extractComment(prop), info.comment);
		generateStereotypes(uri, prop);
		if(info.reference)
			emitStereotype(uri, UML.byreference.getURI());
	}
	
	private void generateStereotypes(String uri, OntResource base) {
		StmtIterator it = base.listProperties(UML.hasStereotype);
		while (it.hasNext()) {
			emitStereotype(uri, it.nextStatement().getResource().getURI());
		}
	}

	private void generatePackage(String uri, OntResource base) {
		Resource symbol = base.getIsDefinedBy();
		if( symbol == null)
			return;
		
		OntResource pack = (OntResource)symbol.as(OntResource.class);
		String curi = constructURI(pack);
		if( ! packages.contains(pack)) {
			emitPackage(curi);
			annotate(curi, pack);
			packages.add(pack);
			generatePackage(curi, pack);
		}
		emitDefinedBy(uri, curi);
	}

	private String extractProfileComment(OntClass base) {
		String comment = null;

		Iterator it = catalog.findProfiles(base).iterator();
		while(it.hasNext()) 
			comment = appendComment(comment, (OntClass) it.next());

		return comment;
	}
	
	private void annotate(String uri, OntResource base) {
		String label = base.getLabel(null);
		if( label != null)
			emitLabel(uri, label);
		String comment = base.getComment(null);
		if( comment != null)
			emitComment(uri, comment, null);
	}
	
	protected abstract void emitLabel(String uri, String label);
	protected abstract void emitComment(String uri, String baseComment, String profileComment);
	protected abstract void emitSuperClass(String subClass, String superClass);
	protected abstract void emitClass(String uri, String base) ;
	protected abstract void emitInstance(String uri, String base, String type);
	protected abstract void emitDatatype(String uri, String xsdtype) ;
	protected abstract void emitObjectProperty(String uri, String base, String domain, String range, boolean required, boolean functional) ;
	protected abstract void emitInverse(String uri, String iuri) ;
	protected abstract void emitStereotype(String uri, String iuri) ;
	protected abstract void emitDatatypeProperty(String uri, String base, String domain, String type, String xsdtype, boolean required) ;
	protected abstract void emitDefinedBy(String uri, String container);
	protected abstract void emitPackage(String uri) ;
	
	protected void log(String string, RDFNode node) {
		log(string + ": " + node);
	}
	
	protected void log(String item) {
		System.out.println(item);
	}
}