# Channel Configuration

TODO : this guide / notes. 


Notes : 

- [ ] describe how `organizations/` folder is populated by the export_msp.sh scripts 
- [ ] configtx uses the internal k8s `$service.svc.cluster.local` DNS domain to communicate between nodes. 
- [ ] describe configtx.yaml assumes / enforces working dir is FABRIC_CFG_PATH

TODOs: 

- [ ] Deploy org nodes across multiple namespaces.  Use kube DNS to resolve in the channel config.  `$service.$namespace.svc.cluster.local`
- [ ] Deploy org nodes across multiple k8s clusters.  Use INGRESS URLs to resolve services. `$ingress-hostname.$org.localho.st:443`
- 