package org.semanticweb.HermiT.existentials;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.io.Serializable;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.semanticweb.HermiT.blocking.*;
import org.semanticweb.HermiT.model.*;
import org.semanticweb.HermiT.tableau.*;

public class IndividualReuseStrategy implements ExistentialsExpansionStrategy,Serializable {
    private static final long serialVersionUID=-7373787507623860081L;
    
    protected final BlockingStrategy m_blockingStrategy;
    protected final boolean m_isDeterministic;
    protected final Map<AtomicConcept,NodeBranchingPointPair> m_reusedNodes;
    protected final Set<AtomicConcept> m_dontReueseConceptsThisRun;
    protected final Set<AtomicConcept> m_dontReueseConceptsEver;
    protected final TupleTable m_reuseBacktrackingTable;
    protected final Object[] m_auxiliaryBuffer;
    protected int[] m_indicesByBranchingPoint;
    protected Tableau m_tableau;
    protected ExtensionManager m_extensionManager;
    protected ExistentialExpansionManager m_existentialExpansionManager;
    
    public IndividualReuseStrategy(BlockingStrategy blockingStrategy,boolean isDeterministic) {
        m_blockingStrategy=blockingStrategy;
        m_isDeterministic=isDeterministic;
        m_reusedNodes=new HashMap<AtomicConcept,NodeBranchingPointPair>();
        m_dontReueseConceptsThisRun=new HashSet<AtomicConcept>();
        m_dontReueseConceptsEver=new HashSet<AtomicConcept>();
        m_reuseBacktrackingTable=new TupleTable(1);
        m_auxiliaryBuffer=new Object[1];
        m_indicesByBranchingPoint=new int[10];
    }
    public void intialize(Tableau tableau) {
        m_tableau=tableau;
        m_extensionManager=m_tableau.getExtensionManager();
        m_existentialExpansionManager=m_tableau.getExistentialExpansionManager();
        m_dontReueseConceptsEver.clear();
//        loadNotReusedConcepts("c:\\Temp\\dont-reuse.txt");
        m_blockingStrategy.initialize(tableau);
    }
    public void clear() {
        m_reusedNodes.clear();
        m_reuseBacktrackingTable.clear();
        m_indicesByBranchingPoint[m_tableau.getCurrentBranchingPoint().getLevel()]=m_reuseBacktrackingTable.getFirstFreeTupleIndex();
        m_blockingStrategy.clear();
    }
    public boolean expandExistentials() {
        m_blockingStrategy.computeBlocking();
        Node node=m_tableau.getFirstTableauNode();
        while (node!=null) {
            if (node.isActive() && !node.isBlocked() && node.hasUnprocessedExistentials()) {
                while (node.hasUnprocessedExistentials()) {
                    ExistentialConcept existentialConcept=node.getSomeUnprocessedExistential();
                    if (existentialConcept instanceof AtLeastAbstractRoleConcept) {
                        AtLeastAbstractRoleConcept atLeastAbstractRoleConcept=(AtLeastAbstractRoleConcept)existentialConcept;
                        boolean isExistentialSatisfied=m_existentialExpansionManager.isSatisfied(atLeastAbstractRoleConcept,node);
                        // Mark the existential as processed BEFORE any branching takes place
                        m_existentialExpansionManager.markExistentialProcessed(atLeastAbstractRoleConcept,node);
                        if (!isExistentialSatisfied) {
                            if (!m_existentialExpansionManager.tryFunctionalExpansion(atLeastAbstractRoleConcept,node)) {
                                LiteralConcept toConcept=atLeastAbstractRoleConcept.getToConcept();
                                if (toConcept instanceof AtomicConcept && shoudReuse((AtomicConcept)toConcept) && atLeastAbstractRoleConcept.getNumber()==1)
                                    expandWithReuse(atLeastAbstractRoleConcept,node);
                                else
                                    m_existentialExpansionManager.doNormalExpansion(atLeastAbstractRoleConcept,node);
                            }
                        }
                        else {
                            if (m_tableau.getTableauMonitor()!=null)
                                m_tableau.getTableauMonitor().existentialSatisfied(atLeastAbstractRoleConcept,node);
                        }
                    }
                    else
                        throw new IllegalStateException("Unsupported type of existential concept in IndividualReuseStrategy.");
                }
                return true;
            }
            node=node.getNextTableauNode();
        }
        return false;
    }
    protected void expandWithReuse(AtLeastAbstractRoleConcept atLeastAbstractRoleConcept,Node node) {
        if (m_tableau.getTableauMonitor()!=null)
            m_tableau.getTableauMonitor().existentialExpansionStarted(atLeastAbstractRoleConcept,node);
        DependencySet dependencySet=m_extensionManager.getConceptAssertionDependencySet(atLeastAbstractRoleConcept,node);
        AtomicConcept toAtomicConcept=(AtomicConcept)atLeastAbstractRoleConcept.getToConcept();
        Node existentialNode;
        NodeBranchingPointPair reuseInfo=m_reusedNodes.get(toAtomicConcept);
        if (reuseInfo==null) {
            if (!m_isDeterministic) {
                BranchingPoint branchingPoint=new IndividualResueBranchingPoint(m_tableau,atLeastAbstractRoleConcept,node);
                m_tableau.pushBranchingPoint(branchingPoint);
                dependencySet=m_tableau.getDependencySetFactory().addBranchingPoint(dependencySet,branchingPoint.getLevel());
            }
            existentialNode=m_tableau.createNewRootNode(dependencySet,0);
            reuseInfo=new NodeBranchingPointPair(existentialNode,m_tableau.getCurrentBranchingPoint().getLevel());
            m_reusedNodes.put(toAtomicConcept,reuseInfo);
            m_extensionManager.addConceptAssertion(toAtomicConcept,existentialNode,dependencySet);
            m_auxiliaryBuffer[0]=toAtomicConcept;
            m_reuseBacktrackingTable.addTuple(m_auxiliaryBuffer);
        }
        else {
            dependencySet=reuseInfo.m_node.addCacnonicalNodeDependencySet(dependencySet);
            existentialNode=reuseInfo.m_node.getCanonicalNode();
            dependencySet=m_tableau.getDependencySetFactory().addBranchingPoint(dependencySet,reuseInfo.m_branchingPoint);
        }
        m_extensionManager.addRoleAssertion(atLeastAbstractRoleConcept.getOnAbstractRole(),node,existentialNode,dependencySet);
        if (m_tableau.getTableauMonitor()!=null)
            m_tableau.getTableauMonitor().existentialExpansionFinished(atLeastAbstractRoleConcept,node);
    }
    protected boolean shoudReuse(AtomicConcept toConcept) {
        return !toConcept.getURI().startsWith("internal:") && !m_dontReueseConceptsThisRun.contains(toConcept) && !m_dontReueseConceptsEver.contains(toConcept);
    }
    public void assertionAdded(Concept concept,Node node) {
        m_blockingStrategy.assertionAdded(concept,node);
    }
    public void assertionRemoved(Concept concept,Node node) {
        m_blockingStrategy.assertionRemoved(concept,node);
    }
    public void assertionAdded(AtomicAbstractRole atomicAbstractRole,Node nodeFrom,Node nodeTo) {
        m_blockingStrategy.assertionAdded(atomicAbstractRole,nodeFrom,nodeTo);
    }
    public void assertionRemoved(AtomicAbstractRole atomicAbstractRole,Node nodeFrom,Node nodeTo) {
        m_blockingStrategy.assertionRemoved(atomicAbstractRole,nodeFrom,nodeTo);
    }
    public void nodeStatusChanged(Node node) {
        m_blockingStrategy.nodeStatusChanged(node);
    }
    public void nodeDestroyed(Node node) {
        m_blockingStrategy.nodeDestroyed(node);
    }
    public void branchingPointPushed() {
        int start=m_tableau.getCurrentBranchingPoint().getLevel();
        int requiredSize=start+1;
        if (requiredSize>m_indicesByBranchingPoint.length) {
            int newSize=m_indicesByBranchingPoint.length*3/2;
            while (requiredSize>newSize)
                newSize=newSize*3/2;
            int[] newIndicesByBranchingPoint=new int[newSize];
            System.arraycopy(m_indicesByBranchingPoint,0,newIndicesByBranchingPoint,0,m_indicesByBranchingPoint.length);
            m_indicesByBranchingPoint=newIndicesByBranchingPoint;
        }
        m_indicesByBranchingPoint[start]=m_reuseBacktrackingTable.getFirstFreeTupleIndex();
    }
    public void backtrack() {
        int requiredFirstFreeTupleIndex=m_indicesByBranchingPoint[m_tableau.getCurrentBranchingPoint().getLevel()];
        for (int index=m_reuseBacktrackingTable.getFirstFreeTupleIndex()-1;index>=requiredFirstFreeTupleIndex;--index) {
            AtomicConcept reuseConcept=(AtomicConcept)m_reuseBacktrackingTable.getTupleObject(index,0);
            Object result=m_reusedNodes.remove(reuseConcept);
            assert result!=null;
        }
        m_reuseBacktrackingTable.truncate(requiredFirstFreeTupleIndex);
    }
    public void modelFound() {
        m_dontReueseConceptsEver.addAll(m_dontReueseConceptsThisRun);
    }
    public boolean isDeterministic() {
        return m_isDeterministic;
    }
    public AtomicConcept getConceptForNode(Node node) {
        for (Map.Entry<AtomicConcept,NodeBranchingPointPair> entry : m_reusedNodes.entrySet())
            if (entry.getValue().m_node==node)
                return entry.getKey();
        return null;
    }
    protected void loadNotReusedConcepts(String fileName) {
        try {
            BufferedReader reader=new BufferedReader(new FileReader(fileName));
            try {
                String line=reader.readLine();
                while (line!=null) {
                    m_dontReueseConceptsEver.add(AtomicConcept.create(line));
                    line=reader.readLine();
                }
            }
            finally {
                reader.close();
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Can't read the nonreused concepts.",e);
        }
    }
    
    protected class IndividualResueBranchingPoint extends BranchingPoint {
        private static final long serialVersionUID=-5715836252258022216L;

        protected final AtLeastAbstractRoleConcept m_existential;
        protected final Node m_node;

        public IndividualResueBranchingPoint(Tableau tableau,AtLeastAbstractRoleConcept existential,Node node) {
            super(tableau);
            m_existential=existential;
            m_node=node;
        }
        public void startNextChoice(Tableau tableau,DependencySet clashDepdendencySet) {
            m_dontReueseConceptsThisRun.add((AtomicConcept)m_existential.getToConcept());
            DependencySet dependencySet=m_tableau.getDependencySetFactory().removeBranchingPoint(clashDepdendencySet,m_level);
            if (m_tableau.getTableauMonitor()!=null)
                m_tableau.getTableauMonitor().existentialExpansionStarted(m_existential,m_node);
            Node existentialNode=tableau.createNewTreeNode(dependencySet,m_node);
            m_extensionManager.addConceptAssertion(m_existential.getToConcept(),existentialNode,dependencySet);
            m_extensionManager.addRoleAssertion(m_existential.getOnAbstractRole(),m_node,existentialNode,dependencySet);
            if (m_tableau.getTableauMonitor()!=null)
                m_tableau.getTableauMonitor().existentialExpansionFinished(m_existential,m_node);
        }
    }
    
    protected static class NodeBranchingPointPair {
        protected final Node m_node;
        protected final int m_branchingPoint;
        
        public NodeBranchingPointPair(Node node,int branchingPoint) {
            m_node=node;
            m_branchingPoint=branchingPoint;
        }
    }
}
