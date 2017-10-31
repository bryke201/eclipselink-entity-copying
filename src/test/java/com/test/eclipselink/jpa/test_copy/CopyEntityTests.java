package com.test.eclipselink.jpa.test_copy;

import static org.junit.Assert.assertTrue;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.eclipse.persistence.config.QueryHints;
import org.eclipse.persistence.internal.jpa.EntityManagerFactoryImpl;
import org.eclipse.persistence.jpa.JpaEntityManager;
import org.eclipse.persistence.queries.FetchGroup;
import org.eclipse.persistence.queries.FetchGroupTracker;
import org.eclipse.persistence.sessions.CopyGroup;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.test.eclipselink.jpa.test_copy.entities.AssociateEntity;
import com.test.eclipselink.jpa.test_copy.entities.AssociateEntity_1;
import com.test.eclipselink.jpa.test_copy.entities.DeepAssociateEntity;
import com.test.eclipselink.jpa.test_copy.entities.RootEntity;

/**
 * 
 * @author bryke
 *
 * 
 * Cloning a JPA entity is definitely no simple task; a provider could make it really easy to do, but there's probably going
 * to be [a lot] of strings attached - especially if these entities are partially fetched (i.e. have lazy attributes and associations).
 * 
 * It may even be a questionable decision when an entity is to be cloned (serialization is another way); it might actually be a sign that
 * the creation of data transfer objects or independent data forms is in order.
 * 
 * Of course, if cloning is desirable, a lot of thought should have been put on making this decision.
 * 
 * This test set works with the following setup: 
 *      -the Eclipselink version used is 2.5.2
 *      -RootEntity is composed of two basic fields and two "toOne" associations, where each association depicts a common way of mapping
 *      -AssociateEntity has a LAZY basic field relevant to demonstrations in this test set
 *      	-depending on modeling requirements and tuning, setting ALL associations to be lazy by default ("toMany" associations are already lazy
 *       	 by default) is arguably good practice
 *      -each ToOne association is declared LAZY
 *      -the Eclipselink L2 cache is turned off (see persistence.xml property "eclipselink.cache.shared.default")
 *         	so each context made is clear of cached entities
 *      -the full set (by default) of woven capabilities of Eclipselink are used; using only a subset can introduce
 *         	many more complications, which would dramatically increase the things to be observed, so it was avoided
 *      -RootEntity's "association" association is annotated with @PrivateOwned for the CASCADE_PRIVATE_PARTS set of tests
 *
 * For now, let's set up the environment. 
 *
 */
public class CopyEntityTests {

	private static EntityManagerFactory emf;

	@BeforeClass
	public static void initialize() {
		emf = Persistence.createEntityManagerFactory("testPU");
		EntityManager em = createEM();

		RootEntity rootEntity = new RootEntity();
		rootEntity.setId(1L);
		rootEntity.setData1("Root:1L:Data1");
		rootEntity.setData2("Root:1L:Data2");

		DeepAssociateEntity deepAssoc = new DeepAssociateEntity();
		deepAssoc.setId(1L);
		deepAssoc.setData1("DeepAssoc:1L:Data1");
		deepAssoc.setData2("DeepAssoc:1L:Data2");

		AssociateEntity assocEnt = new AssociateEntity();
		assocEnt.setId(1L);
		assocEnt.setData1("Assoc:1L:Data1");
		assocEnt.setData2("Assoc:1L:Data2");
		assocEnt.setLazyData3("Assoc:1L:LazyData3");

		AssociateEntity_1 circularAssoc = new AssociateEntity_1();
		circularAssoc.setId(1L);
		circularAssoc.setData1("Assoc_1:1L:Data1");
		circularAssoc.setData2("Assoc_1:1L:Data2");

		em.getTransaction().begin();

		rootEntity = em.merge(rootEntity);
		assocEnt = em.merge(assocEnt);
		assocEnt.setDeepAssocEntity(em.merge(deepAssoc));
		rootEntity.setAssociation(assocEnt);

		circularAssoc.setCircularRef(rootEntity);
		rootEntity.setAssociation1(em.merge(circularAssoc));

		em.getTransaction().commit();
		em.close();
	}

	@AfterClass
	public static void tearDown() {
		emf.close();
	}

	private static EntityManager createEM() {
		return emf.createEntityManager();
	}

	private static <T> T copyEntity(EntityManager em, T entity, CopyGroup cg) {
		JpaEntityManager jem = (JpaEntityManager) em;
		return (T) jem.copy(entity, cg);
	}

	private static FetchGroup applyFetchGroup(Query query, FetchGroup fg) {
		System.out.println("TO APPLY FG: " + fg);
		query.setHint(QueryHints.FETCH_GROUP, fg);
		return fg;
	}

	private static void assertAttributeInitialized(Object entity, String attributeName) {
		assertTrue("The attribute '" + attributeName + "' should have been initialized.",
				isAttributeInitialized(entity, attributeName));
	}

	private static void assertAttributeNotInitialized(Object entity, String attributeName) {
		assertTrue("The attribute '" + attributeName + "' should not have been initialized.",
				!isAttributeInitialized(entity, attributeName));
	}

	private static void assertAssociationInitialized(Object entity, String associationName) {
		assertTrue("The association '" + associationName + "' should have been initialized.",
				isAttributeInitialized(entity, associationName));
	}

	private static void assertAssociationNotInitialized(Object entity, String associationName) {
		assertTrue("The association '" + associationName + "' should not have been initialized.",
				!isAttributeInitialized(entity, associationName));
	}

	/**
	 * 
	 * The code used to ascertain fetch state may seem complex (which it is); this is because
	 * the Eclipselink implementation is unreliable when it comes to entities that were fetched with an accompanying
	 * custom FetchGroup.
	 * 
	 * This blog post should shed light on this: 
	 * 
	 * http://briaguy.blogspot.com/2017/09/eclipselink-252-jpa-210-determining.html
	 * 
	 */
	private static boolean isAttributeInitialized(Object entity, String attributeName) {
		if (!(entity instanceof FetchGroupTracker)) {
			throw new IllegalArgumentException(
					"The entity is not configured for tracking fetch groups. Perhaps weaving was not enabled for this entity or at all.");
		} else {
			return ((EntityManagerFactoryImpl) emf).isLoaded(entity, attributeName);
		}
	}

	/**
	 * 
	 *  Entity Copying
	 * 
	 *  Although also simply called "cloning", it differentiates from normal Java
	 *  cloning (via Cloneable) in that this Eclipselink feature makes use of an
	 *  EntityManager and can be made aware of unfetched attributes and associations in
	 *  entities; otherwise, special care has to be taken when implementing Java cloning for entities 
	 *  that have only been partially fetched. 
	 * 
	 *  org.eclipse.persistence.internal.jpa.EntityManagerImpl.EntityManagerImpl's copy method pretty much handles copying.
	 *  It has the following signature:
	 *  
	 *  public Object copy(Object entityOrEntities, AttributeGroup group)
	 *  
	 *  NOTE: make sure the entity to be copied is part of the persistence unit with which the entity manager to be used is built. Else,
	 *  the entity itself will simply be returned as the result.
	 *  
	 *  The method also comes with a comment:
	 *  
	 *  	"This method will return copy the passed entity using the passed AttributeGroup. 
	 *  	 In case of collection all members should be either entities of the same type or 
	 *  	 have a common inheritance hierarchy mapped root class. The AttributeGroup should 
	 *  	 correspond to the entity type."
	 *  
	 *  An AttributeGroup is pretty much Eclipselink's way of allowing runtime declaration of sets of attributes
	 *  which can be used (also interchangeably in some cases) in a variety of ways; e.g. a FetchGroup is an AttributeGroup
	 *  that is usually used to specify which attributes a query should only query for.
	 *  
	 *  Although the method accepts an AttributeGroup, it is more intuitive and configurable to use its
	 *  org.eclipse.persistence.sessions.CopyGroup implementation.
	 *  
	 *  The most notable configuration option of a CopyGroup is its cascade level; i.e. whether it should include certain types
	 *  (or depth level) of associations in the resulting clone.
	 * 
	 * It comes in three main configurations: 
	 *  -CASCADE_PRIVATE_PARTS, the default, where only "privately owned" associations are cascaded
	 * 	-CASCADE_ALL_PARTS, where ALL ASSOCIATIONS are copied (and initialized along the way) 
	 *  -CASCADE_TREE, where only attributes present in the attribute group are copied, and
	 * 		accessing unfetched attributes in the resulting copy would throw an
	 * 		exception (this is great for debugging fetch groups);
	 *  -NO_CASCADE; self-explanatory
	 * 
	 * Additionally, it can be specified whether primary keys and/or version column should be reset (nulled), via the
	 * following methods, respectively:
	 * 	
	 * 		-setShouldResetPrimaryKey(boolean)
	 * 		-setShouldResetVersion(boolean)
	 * 
	 * It is worth noting that the effects of these also cascade to associations of the resulting copy.
	 * 
	 * NOTE: though not explicitly mentioned, primary key resetting differs in behavior, depending on whether
	 * the CopyGroup uses CASCADE_TREE or not:
	 * 
	 * 		-if the CopyGroup uses CASCADE_TREE:
	 * 			-if an empty group is used, the primary key reset setting is ignored since ALL ATTRIBUTES will be copied anyway
	 * 			-if a group with attributes is used, and setShouldResetPrimaryKey is called with "true", then
	 * 				ONLY BASIC KEYS WILL BE RESET; entity reference keys will still be present in the copy
	 * 
	 * 		-if the CopyGroup uses any other cascade type, and setShouldResetPrimaryKey is called with "true", then
	 * 				THE KEYS WILL ONLY BE RESET IF NO ENTITY REFERENCE KEYS ARE PRESENT - it is all or nothing
	 * 
	 */

	/**
	 * 
	 * 
	 * !!!!!! IMPORTANT NOTE !!!!!!!
	 * 
	 * -Adding an attribute to a CopyGroup will shift set its cascade level to CASCADE_TREE!
	 * 	-if the code for addAttribute(String name) is traced, it calls an overloaded addAttribute method that is overridden in CopyGroup, which
	 * 		FIRST SETS THE CASCADE DEPTH TO CASCADE_TREE
	 * 	-just make sure the set cascade call it not succeeded by any attribute addition
	 *  
	 * 
	 */

	/**
	 * =========================================================================================================
	 * CASCADE_ALL_PARTS
	 * =========================================================================================================
	 * 
	 */

	@Test
	// With an empty CopyGroup and no FetchGroup, all associations and fields
	// should be initialized using default configuration, and copying will initialize the associations using further
	// default configuration
	public void CASC_ALL_EMPTY_CG__NO_FG() {
		EntityManager em = createEM();

		RootEntity rootEnt = findRootEntityById(em, 1L);

		//at this point, rootEnt should be loaded with its defaults:
		assertAttributeInitialized(rootEnt, "data1");
		assertAttributeInitialized(rootEnt, "data2");
		assertAssociationNotInitialized(rootEnt, "association");
		assertAssociationNotInitialized(rootEnt, "association1");

		CopyGroup cg = new CopyGroup();
		cg.cascadeAllParts();

		RootEntity rootEntCopy = copyEntity(em, rootEnt, cg);

		//because cascade_all_parts affects associations, they should be initialized now:
		assertAssociationInitialized(rootEntCopy, "association");
		assertAssociationInitialized(rootEntCopy, "association1");
		assertAssociationInitialized(rootEntCopy.getAssociation(), "deepAssocEntity");
		assertAssociationInitialized(rootEntCopy.getAssociation1(), "circularRef");

		//for LAZY (or uninitialized due to runtime FetchGroups) basic attributes, we use null checks
		//this is because associations initialized due to copying
		//are pretty much unaware of fetch state (as everything in the association is now deemed "initialized")
		//furthermore, because cascading refers to ASSOCIATIONS, lazily loaded BASIC attributes are NULLed instead
		//the attribute "lazyData3" of "association" was given an initial value of "Assoc:1L:LazyData3"; which is now null thanks to being
		//a LAZY BASIC attribute
		assertTrue(rootEntCopy.getAssociation().getLazyData3() == null);

		//copying even does back-referencing!
		//i.e. entities with the same key  refer to the same object
		assertTrue(rootEntCopy.getAssociation1().getCircularRef() == rootEntCopy);

		em.close();
	}

	@Test
	// using cascade_all_parts on a CopyGroup will have it ignore the attributes it contains
	public void CASC_ALL_ignores_atts() {
		EntityManager em = createEM();

		RootEntity rootEnt = findRootEntityById(em, 1L);

		//at this point, rootEnt should be loaded with its defaults:
		assertAttributeInitialized(rootEnt, "data1");
		assertAttributeInitialized(rootEnt, "data2");
		assertAssociationNotInitialized(rootEnt, "association");
		assertAssociationNotInitialized(rootEnt, "association1");

		//again, notice that cascadeAllParts() is called at the end so that it is the ACTUAL cascade type used
		//-because as mentioned, adding attributes turns it into CASCADE_TREE
		CopyGroup cg = new CopyGroup();
		cg.addAttribute("data2");
		cg.addAttribute("association.lazyData3");
		cg.cascadeAllParts();
		cg.setShouldResetPrimaryKey(true);
		RootEntity rootEntCopy = copyEntity(em, rootEnt, cg);

		//because cascade_all_parts initializes ALL associations, they should be initialized now:
		assertAssociationInitialized(rootEntCopy, "association");
		assertAssociationInitialized(rootEntCopy, "association1");
		assertAssociationInitialized(rootEntCopy.getAssociation(), "deepAssocEntity");
		assertAssociationInitialized(rootEntCopy.getAssociation1(), "circularRef");

		//lazyData3 is not initialized even if it is part of the CopyGroup (because the attributes are ignored)
		//it is given a NULL value again
		assertTrue(rootEntCopy.getAssociation().getLazyData3() == null);

		//the other attributes should be fine
		assertTrue(rootEntCopy.getAssociation().getData1() != null);
		assertTrue(rootEntCopy.getAssociation().getData2() != null);

		//it is also noteworthy that the original entities can be affected;
		//the original root's associations are now initialized due to copying:
		assertAssociationInitialized(rootEnt, "association");
		assertAssociationInitialized(rootEnt, "association1");
		assertAssociationInitialized(rootEnt.getAssociation(), "deepAssocEntity");
		assertAssociationInitialized(rootEnt.getAssociation1(), "circularRef");

		em.close();
	}

	private RootEntity findRootEntityById(EntityManager em, Long id) {
		return findRootEntityById(em, id, null);
	}

	private RootEntity findRootEntityById(EntityManager em, Long id, FetchGroup fg) {
		TypedQuery<RootEntity> query = em.createQuery("SELECT o FROM RootEntity o WHERE o.id = :id", RootEntity.class);
		query.setParameter("id", id);
		if (fg != null) {
			applyFetchGroup(query, fg);
		}
		return query.getSingleResult();
	}

	@Test
	//this time, a FetchGroup is used with the initial query
	public void CASC_ALL_ATT_CG__WITH_FG() {
		EntityManager em = createEM();

		FetchGroup fg = new FetchGroup();
		fg.addAttribute("data2");
		fg.addAttribute("association.data1");
		fg.addAttribute("association.lazyData3");

		RootEntity rootEnt = findRootEntityById(em, 1L, fg);

		//the entity follows the fetch group:
		assertAttributeInitialized(rootEnt, "data2");
		assertAttributeNotInitialized(rootEnt, "data1");
		assertAssociationNotInitialized(rootEnt, "association1");
		//initialize "association" and check if it follows the fetch group
		AssociateEntity assoc = rootEnt.getAssociation();
		assertAttributeInitialized(assoc, "data1");
		assertAttributeInitialized(assoc, "lazyData3");
		assertAttributeNotInitialized(assoc, "data2");
		assertAssociationNotInitialized(assoc, "deepAssocEntity");

		//a CopyGroup can also be obtained from a FetchGroup via toCopyGroup()
		//this will have the same effect if cascadeAllParts() is called
		CopyGroup cg = new CopyGroup();
		cg.addAttribute("data2");
		cg.addAttribute("association.data2");
		cg.cascadeAllParts();

		RootEntity rootEntCopy = copyEntity(em, rootEnt, cg);

		//the copy should follow the FetchGroup for the root and its "association":
		assertTrue(rootEntCopy.getData1() == null);
		assertTrue(rootEntCopy.getData2() != null);
		assertTrue(rootEntCopy.getAssociation().getData1() != null);
		assertTrue(rootEntCopy.getAssociation().getData2() == null);
		assertTrue(rootEntCopy.getAssociation().getLazyData3() != null);

		//while the other associations get initialized like usual
		assertTrue(rootEntCopy.getAssociation().getDeepAssocEntity().getData1() != null);
		assertTrue(rootEntCopy.getAssociation1().getData1() != null);
		assertTrue(rootEntCopy.getAssociation1().getCircularRef() == rootEntCopy);
	}

	/**
	 * 
	 * =========================================================================================================
	 * CASCADE_TREE
	 * =========================================================================================================
	 * 
	 * -this mode will probably see the most use, as it is normally counterintuitive to want to cascade ALL associations
	 * right away
	 * 
	 */

	@Test
	//first, let's find out what happens if an empty copy group is used with CASCADE_TREE
	public void CASC_TREE_EMPTY_GROUP() {
		EntityManager em = createEM();

		FetchGroup fg = new FetchGroup();
		fg.addAttribute("data2");
		fg.addAttribute("association.data1");
		fg.addAttribute("association.lazyData3");

		RootEntity rootEnt = findRootEntityById(em, 1L, fg);

		//the entity follows the fetch group:
		assertAttributeInitialized(rootEnt, "data2");
		assertAttributeNotInitialized(rootEnt, "data1");
		assertAssociationNotInitialized(rootEnt, "association1");
		//initialize "association" and check if it follows the fetch group
		AssociateEntity assoc = rootEnt.getAssociation();
		assertAttributeInitialized(assoc, "data1");
		assertAttributeInitialized(assoc, "lazyData3");
		assertAttributeNotInitialized(assoc, "data2");
		assertAssociationNotInitialized(assoc, "deepAssocEntity");

		//an empty copy group!
		//CASCADE_TREE relies on the attributes in the its copy group!
		CopyGroup cg = new CopyGroup();
		cg.cascadeTree();

		//should execute a couple of queries; breakpoint here and clear the log to track them
		RootEntity rootEntCopy = copyEntity(em, rootEnt, cg);

		/**
		 * The following is a comment in the code for copying using a CASCADE_TREE copy group:
		 * 
		 *  "empty copy group means all the attributes should be copied - don't alter it."
		 *  
		 * As it mentions, an empty copy group creates a copy with ALL attributes. How deep though?
		 * 
		 * Of course, this means that a query is done to initialize attributes not present in fetch group used
		 * in the initial query (use debug breakpoints to check out the queries).
		 */
		//all of the root's attributes and associations seem to have been initialized, as per 
		//the queries executed during copy
		assertAttributeInitialized(rootEntCopy, "data1");
		assertAttributeInitialized(rootEntCopy, "data2");
		assertAssociationInitialized(rootEntCopy, "association");
		assertAssociationInitialized(rootEntCopy, "association1");

		//however, contrary to the initial FetchGroup, the association "association" is fetched differently:
		//the fetch state we find in the following assertions is reminiscent of the DEFAULT fetch state for this association
		//DO NOT BE FOOLED! Even if we see a query for "association" that includes "lazyData3", IT IS NOT PART OF THE COPY!
		assertAttributeInitialized(rootEntCopy.getAssociation(), "data1");
		assertAttributeInitialized(rootEntCopy.getAssociation(), "data2");
		assertAttributeNotInitialized(rootEntCopy.getAssociation(), "lazyData3");
		assertAssociationNotInitialized(rootEntCopy.getAssociation(), "deepAssocEntity");

		//it seems that "ALL attributes" only means the ones IMMEDIATE TO THE ENTITY BEING COPIED
		//let's confirm that "association1" is also fetch using its defaults:
		assertAttributeInitialized(rootEntCopy.getAssociation1(), "data1");
		assertAttributeInitialized(rootEntCopy.getAssociation1(), "data2");
		//notice that this is also not initialized even if this association points back to the root
		assertAssociationNotInitialized(rootEntCopy.getAssociation1(), "circularRef");

		/**
		 * 
		 * !! IMPORTANT NOTE !!
		 * 
		 * Notice that this time, we checked using proper fetch state querying instead of null checks.
		 * 
		 * This is because copies produced under the CASCADE_TREE depth are aware of their fetch state;
		 * When an attribute that is not specified in the copy group is accessed, an IllegalStateException is thrown!
		 * 
		 * This becomes very useful for debugging FetchGroups!
		 * 
		 */
		//observe the following:
		IllegalStateException illegalStateExc = null;
		try {
			rootEntCopy.getAssociation().getDeepAssocEntity();
		} catch (IllegalStateException e) {
			illegalStateExc = e;
		}
		assertTrue(
				"An IllegalStateException should have been thrown as the accessed attribute was not specified to appear in the copy.",
				illegalStateExc != null);

		//==============

		//finally, let's find out how original entities are affected:
		//root
		assertAttributeInitialized(rootEnt, "data1");
		assertAssociationInitialized(rootEnt, "association1");
		//root's association
		assertAttributeInitialized(rootEnt.getAssociation(), "data2");
		//-deepAssocEntity did not need to be initialized as it is lazy by DEFAULT initialization
		assertAssociationNotInitialized(rootEnt.getAssociation(), "deepAssocEntity");
		//root's association1
		assertAttributeInitialized(rootEnt.getAssociation1(), "data1");
		assertAttributeInitialized(rootEnt.getAssociation1(), "data2");
		//-circularRef is also lazy by default, so it did not get initialized
		assertAssociationNotInitialized(rootEnt.getAssociation1(), "circularRef");

		/**
		 * 
		 * It seems that whatever was initialized during copying also gets initialized in the original entities.
		 * 
		 * 
		 */
	}

	@Test
	//let's just confirm that an empty copy group that initialization "ALL attributes"
	//only initializes the ones IMMEDIATE TO THE ENTITY TO BE COPIED
	public void CASC_TREE_EMPTY_GROUP_copy_association() {
		EntityManager em = createEM();

		FetchGroup fg = new FetchGroup();
		fg.addAttribute("data2");
		fg.addAttribute("association.data1");
		fg.addAttribute("association.lazyData3");

		RootEntity rootEnt = findRootEntityById(em, 1L, fg);

		//the entity follows the fetch group:
		assertAttributeInitialized(rootEnt, "data2");
		assertAttributeNotInitialized(rootEnt, "data1");
		assertAssociationNotInitialized(rootEnt, "association1");
		//initialize "association" and check if it follows the fetch group
		AssociateEntity assoc = rootEnt.getAssociation();
		assertAttributeInitialized(assoc, "data1");
		assertAttributeInitialized(assoc, "lazyData3");
		assertAttributeNotInitialized(assoc, "data2");
		assertAssociationNotInitialized(assoc, "deepAssocEntity");

		CopyGroup cg = new CopyGroup();
		cg.cascadeTree();

		//instead of copying the root, we copy its "association"
		AssociateEntity assocCopy = copyEntity(em, rootEnt.getAssociation(), cg);

		//confirming that ALL its attributes are copied:
		assertAttributeInitialized(assocCopy, "data1");
		assertAttributeInitialized(assocCopy, "data2");
		assertAttributeInitialized(assocCopy, "lazyData3");
		//because all attributes of deepAssocEntity are eager by default, we need not confirm its contents
		assertAssociationInitialized(assocCopy, "deepAssocEntity");

		//now let's confirm that the original entity also had its own attributes initialized:
		assertAttributeInitialized(rootEnt.getAssociation(), "data1");
		assertAttributeInitialized(rootEnt.getAssociation(), "data2");
		assertAttributeInitialized(rootEnt.getAssociation(), "lazyData3");
		assertAssociationInitialized(rootEnt.getAssociation(), "deepAssocEntity");
	}

	@Test
	//this time, let's try adding attributes into the CopyGroup so that they aren't aligned with the FetchGroup
	public void CASC_TREE_GROUP_with_ATTS() {
		EntityManager em = createEM();

		FetchGroup fg = new FetchGroup();
		fg.addAttribute("data2");
		fg.addAttribute("association.data1");
		fg.addAttribute("association.lazyData3");

		RootEntity rootEnt = findRootEntityById(em, 1L, fg);

		//the entity follows the fetch group:
		assertAttributeInitialized(rootEnt, "data2");
		assertAttributeNotInitialized(rootEnt, "data1");
		assertAssociationNotInitialized(rootEnt, "association1");
		//initialize "association" and check if it follows the fetch group
		AssociateEntity assoc = rootEnt.getAssociation();
		assertAttributeInitialized(assoc, "data1");
		assertAttributeInitialized(assoc, "lazyData3");
		assertAttributeNotInitialized(assoc, "data2");
		assertAssociationNotInitialized(assoc, "deepAssocEntity");

		//no need to call cascadeTree() as this is automatically done when adding attributes
		CopyGroup cg = new CopyGroup();
		cg.addAttribute("data1");
		cg.addAttribute("association.data2");
		cg.setShouldResetPrimaryKey(true);
		//instead of copying the root, we copy its "association"
		RootEntity copyRoot = copyEntity(em, rootEnt, cg);

		//confirming that ONLY the CopyGroup attributes are copied:
		assertAttributeInitialized(copyRoot, "data1");
		assertAssociationInitialized(copyRoot, "association");
		assertAttributeInitialized(copyRoot.getAssociation(), "data2");

		assertAttributeNotInitialized(copyRoot, "data2");
		assertAssociationNotInitialized(copyRoot, "association1");
		assertAttributeNotInitialized(copyRoot.getAssociation(), "data1");
		assertAssociationNotInitialized(copyRoot.getAssociation(), "deepAssocEntity");

		//confirming that the original entities are affected by the initializations
		assertAttributeInitialized(rootEnt, "data1");
		assertAttributeInitialized(rootEnt.getAssociation(), "data2");
	}

	@Test
	//Now, one of the easier things to do is turn an existing FetchGroup into a CopyGroup
	public void CG_from_FG() {
		EntityManager em = createEM();

		FetchGroup fg = new FetchGroup();
		fg.addAttribute("data2");
		fg.addAttribute("association.data1");
		fg.addAttribute("association.lazyData3");

		RootEntity rootEnt = findRootEntityById(em, 1L, fg);

		//the entity follows the fetch group:
		assertAttributeInitialized(rootEnt, "data2");
		assertAttributeNotInitialized(rootEnt, "data1");
		assertAssociationNotInitialized(rootEnt, "association1");
		//initialize "association" and check if it follows the fetch group
		AssociateEntity assoc = rootEnt.getAssociation();
		assertAttributeInitialized(assoc, "data1");
		assertAttributeInitialized(assoc, "lazyData3");
		assertAttributeNotInitialized(assoc, "data2");
		assertAssociationNotInitialized(assoc, "deepAssocEntity");

		//note that the toCopyGroup() method of AttributeGroup (the superclass of most "Group" classes)
		//automatically produces a CASCADE_TREE copy group - this is only natural since use of this method
		//implies the relevance of already incorporated attributes
		CopyGroup cg = fg.toCopyGroup();

		//instead of copying the root, we copy its "association"
		//also, it is worth noting that NO QUERIES ARE EXECUTED
		//-needless to say, this is because the attributes defined in the CopyGroup are already fetched
		//	due to its alignment with the FetchGroup
		RootEntity copyRoot = copyEntity(em, rootEnt, cg);

		//confirming that ONLY the CopyGroup attributes are copied:
		assertAttributeInitialized(copyRoot, "data2");
		assertAssociationInitialized(copyRoot, "association");
		assertAttributeInitialized(copyRoot.getAssociation(), "data1");
		assertAttributeInitialized(copyRoot.getAssociation(), "lazyData3");

		assertAttributeNotInitialized(copyRoot, "data1");
		assertAssociationNotInitialized(copyRoot, "association1");
		assertAttributeNotInitialized(copyRoot.getAssociation(), "data2");
		assertAssociationNotInitialized(copyRoot.getAssociation(), "deepAssocEntity");

		//no need to check if the original entities were affected since no further initialization happened during copying

	}

	@Test
	//Most of the time, however, it is convenient to obtain the FetchGroup from the entity itself
	//let's find out the behavior of such a case
	public void CG_from_ENTITY_FG() {
		EntityManager em = createEM();

		FetchGroup fg = new FetchGroup();
		fg.addAttribute("data2");
		fg.addAttribute("association.data1");
		fg.addAttribute("association.lazyData3");

		RootEntity rootEnt = findRootEntityById(em, 1L, fg);

		//the entity follows the fetch group:
		assertAttributeInitialized(rootEnt, "data2");
		assertAttributeNotInitialized(rootEnt, "data1");
		assertAssociationNotInitialized(rootEnt, "association1");
		//initialize "association" and check if it follows the fetch group
		AssociateEntity assoc = rootEnt.getAssociation();
		assertAttributeInitialized(assoc, "data1");
		assertAttributeInitialized(assoc, "lazyData3");
		assertAttributeNotInitialized(assoc, "data2");
		assertAssociationNotInitialized(assoc, "deepAssocEntity");

		//of course, this fails if the entity was fetched without using a FetchGroup
		FetchGroup fgFromEntity = ((FetchGroupTracker) rootEnt)._persistence_getFetchGroup();
		CopyGroup cg = fgFromEntity.toCopyGroup();

		//oops, an initialization query is executed!
		RootEntity copyRoot = copyEntity(em, rootEnt, cg);

		//one would think that the FetchGroup obtained from the entity would be exactly the same
		//as the one used when querying for it;

		//as it turns out, even if it was the root entity, the FetchGroup is holds only tracks ITS OWN ATTRIBUTES;
		//the fetch state of associations are not tracked by the one we obtain - for those, DEFAULT initialization is used 

		//ONLY THE IMMEDIATE ATTRIBUTES OF ROOT FOLLOW THE FETCHGROUP
		assertAttributeInitialized(copyRoot, "data2");
		assertAssociationInitialized(copyRoot, "association");
		assertAttributeInitialized(copyRoot.getAssociation(), "data1");

		//THOUGH IN THE FETCHGROUP, association's lazyData3 IS NOT INITIALIZED BECAUSE IT IS HANDLED
		//BY association's FetchGroup, AND NOT THE ROOT's FETCHGROUP!
		assertAttributeNotInitialized(copyRoot.getAssociation(), "lazyData3");

		//the result of the association follows DEFAULT initialization
		assertAttributeInitialized(copyRoot.getAssociation(), "data1");
		assertAttributeInitialized(copyRoot.getAssociation(), "data2");
		assertAssociationNotInitialized(copyRoot.getAssociation(), "deepAssocEntity");

		//because an initialization was done, the original "association" is affected:
		assertAttributeInitialized(assoc, "data2");

		/**
		 * 
		 * As it turns out, this way of obtaining a CopyGroup can lead to undesirable results.
		 * 
		 * Keep the FetchGroup you created handy - don't take those from the resulting entities.
		 * 
		 */
	}

	/**
	 * 
	 * =========================================================================================================
	 * CASCADE_PRIVATE_PARTS
	 * =========================================================================================================
	 * 
	 * -this mode pretty much works like CASCADE_ALL_PARTS, except only cascades
	 * associations annotated with @PrivateOwned (org.eclipse.persistence.annotations.PrivateOwned)
	 * 
	 * -what's important to know in this mode is whether accessing an attribute that is not to be copied would
	 * throw an IllegalArgumentException (like in CASCADE_TREE) or not.
	 * 
	 * -RootEntity's "association" association has been marked with @PrivateOwned for this set of tests.
	 */

	@Test
	public void CASC_PRIVATE() {
		EntityManager em = createEM();

		FetchGroup fg = new FetchGroup();
		fg.addAttribute("data2");
		fg.addAttribute("association.data1");
		fg.addAttribute("association.lazyData3");

		RootEntity rootEnt = findRootEntityById(em, 1L, fg);

		//the entity follows the fetch group:
		assertAttributeInitialized(rootEnt, "data2");
		assertAttributeNotInitialized(rootEnt, "data1");
		assertAssociationNotInitialized(rootEnt, "association1");
		//initialize "association" and check if it follows the fetch group
		AssociateEntity assoc = rootEnt.getAssociation();
		assertAssociationInitialized(rootEnt, "association");
		assertAttributeInitialized(assoc, "data1");
		assertAttributeInitialized(assoc, "lazyData3");
		assertAttributeNotInitialized(assoc, "data2");
		assertAssociationNotInitialized(assoc, "deepAssocEntity");

		//let's find out if attributes affect this cascade type
		CopyGroup cg = new CopyGroup();
		//looks like it doesn't care as well; even if we comment the following line, the behavior
		//remains the same
		cg.addAttribute("association.data1");
		cg.cascadePrivateParts();

		//woah, it seems that all associations were deeply queried for
		RootEntity copyRoot = copyEntity(em, rootEnt, cg);

		//In checking the actual contents of the copy, we find that the following
		//attributes and associations have been nulled, while everything else was initialized:
		assertTrue(copyRoot.getData1() == null);
		assertTrue(copyRoot.getAssociation().getData2() == null);
		//it can be surmised that both the root and association at least follow the FetchGroup

		//this one is very sneaky - the copy is able to use indirection classes so that the association
		//can be initialized from the copy!
		//NOTE: you might see "circularRef" as "null" in the debug variables in your IDE -
		//	you should, instead, look for "_persistence_circularRef_vh", which should contain the indirection
		//	object.

		//this copy is quite unpleasantly smart
		assertAssociationNotInitialized(copyRoot.getAssociation1(), "circularRef");

		//when this association is initialized, it becomes a backreference to the root!
		assertTrue(copyRoot.getAssociation1().getCircularRef() == rootEnt);

		//furthermore, it looks like the original entities are affected as well
		//everything is now initialized... EXCEPT FOR THE ONLY ACTUAL PRIVATE-OWNED ASSOCIATION
		assertAttributeInitialized(rootEnt, "data1");
		assertAttributeInitialized(rootEnt, "data2");

		assertAssociationInitialized(rootEnt, "association1");

		assertAttributeInitialized(assoc, "data1");
		assertAttributeInitialized(assoc, "data2");
		assertAttributeInitialized(assoc, "lazyData3");
		assertAssociationInitialized(assoc, "deepAssocEntity");
		assertAttributeInitialized(assoc.getDeepAssocEntity(), "data1");
		assertAttributeInitialized(assoc.getDeepAssocEntity(), "data2");

		assertAttributeInitialized(rootEnt.getAssociation1(), "data1");
		assertAttributeInitialized(rootEnt.getAssociation1(), "data2");

		//This association was already initialized in the root after accessing (line 719), but it seems that 
		//it was REVERTED! This is quite dangerous
		assertAssociationNotInitialized(rootEnt, "association");

		/**
		 * 
		 * It appears that even though CASCADE_PRIVATE_PARTS is the default for CopyGroup,
		 * it is the one with the most peculiar behavior.
		 * 
		 * I might do more research on this mode in the future, but as it is, it does not seem to be of much use
		 * in common cases - at least for me.
		 * 
		 * We were'nt also able to find out how attributes that are not copied behave.
		 * 
		 */
	}

	/**
	 * 
	 * 
	 * =========================================================================================================
	 * NO_CASCADE
	 * =========================================================================================================
	 * 
	 * The final option, which I hope to be very predictable.
	 * 
	 * As the name suggests, this option does not cascade associations
	 * 
	 */

	@Test
	public void NO_CASC() {
		EntityManager em = createEM();

		FetchGroup fg = new FetchGroup();
		fg.addAttribute("data2");
		fg.addAttribute("association.data1");
		fg.addAttribute("association.lazyData3");

		RootEntity rootEnt = findRootEntityById(em, 1L, fg);

		//the entity follows the fetch group:
		assertAttributeInitialized(rootEnt, "data2");
		assertAttributeNotInitialized(rootEnt, "data1");
		assertAssociationNotInitialized(rootEnt, "association1");
		//initialize "association" and check if it follows the fetch group
		AssociateEntity assoc = rootEnt.getAssociation();
		assertAttributeInitialized(assoc, "data1");
		assertAttributeInitialized(assoc, "lazyData3");
		assertAttributeNotInitialized(assoc, "data2");
		assertAssociationNotInitialized(assoc, "deepAssocEntity");

		//let's find out if attributes affect this cascade type
		CopyGroup cg = new CopyGroup();
		//again, it doesn't seem to affect the CopyGroup at all		
		//		cg.addAttribute("association.data1");
		cg.dontCascade();

		RootEntity copyRoot = copyEntity(em, rootEnt, cg);

		//why is association1 initialized and not null?
		assertAssociationInitialized(copyRoot, "association1");
		assertTrue(copyRoot.getAssociation1() != null);

		/**
		 * 
		 * Even with only these findings, we can say that NO_CASC is not to be trusted yet.
		 * there may be a lot of things to note before using this mode, but the results of probable basic use
		 * stray from its naming and contract.
		 * 
		 * Luckily, we witnessed them.
		 */
	}
}
