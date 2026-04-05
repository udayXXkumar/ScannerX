import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Shield, ArrowRight, CheckCircle, AlertTriangle } from 'lucide-react';
import api from '../../api/axios';
import DarkSelect from '../../components/ui/DarkSelect';
import { useWorkspaceScans } from '../../hooks/useWorkspaceScans';
import { sanitizeFindingTitle } from '../../lib/findingUtils';
import { getScanRunLabel } from '../../lib/scanUtils';

const ScanComparison = () => {
  const [scan1Id, setScan1Id] = useState('');
  const [scan2Id, setScan2Id] = useState('');

  const { scans = [], isError: isScanStateError } = useWorkspaceScans();

  const { data: comparison, isFetching: isComparing, refetch } = useQuery({
    queryKey: ['compareScans', scan1Id, scan2Id],
    queryFn: async () => {
        const { data } = await api.get(`/scans/compare?scan1=${scan1Id}&scan2=${scan2Id}`);
        return data;
    },
    enabled: false,
    retry: false
  });

  const handleCompare = () => {
    if (scan1Id && scan2Id) {
        refetch();
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold flex items-center">
          <Shield className="w-6 h-6 mr-2 text-primary" />
          Scan Comparison
        </h1>
      </div>

      <div className="bg-bg-panel border border-border-subtle rounded-xl p-6">
        <h3 className="text-lg font-medium text-gray-200 mb-4">Select Scans to Compare</h3>
        <p className="text-sm text-gray-400 mb-6">Compare two scans of the same target to understand vulnerability drift (newly discovered vs resolved issues).</p>

        {isScanStateError ? (
          <div className="mb-6 rounded-xl border border-rose-500/16 bg-rose-500/8 px-4 py-3 text-sm text-rose-100">
            Scan history is temporarily unavailable. Refresh or sign in again before comparing scans.
          </div>
        ) : null}
        
        <div className="flex items-center space-x-4 mb-6">
            <DarkSelect
              value={scan1Id}
              onChange={setScan1Id}
              className="flex-1"
              placeholder="Select Base Scan (Older)"
              options={scans.map((scan) => ({ value: String(scan.id), label: getScanRunLabel(scan) }))}
            />
            <ArrowRight className="w-5 h-5 text-gray-400" />
            <DarkSelect
              value={scan2Id}
              onChange={setScan2Id}
              className="flex-1"
              placeholder="Select Target Scan (Newer)"
              options={scans.map((scan) => ({ value: String(scan.id), label: getScanRunLabel(scan) }))}
            />
            <button 
                onClick={handleCompare}
                disabled={!scan1Id || !scan2Id || scan1Id === scan2Id}
                className="bg-primary hover:bg-primary-hover text-bg-base font-semibold py-2 px-6 rounded-lg transition-colors disabled:opacity-50"
            >
                Compare
            </button>
        </div>
        
        {isComparing && <div className="text-gray-400">Comparing scans...</div>}
        
        {comparison && (
            <div className="mt-8 space-y-6">
               <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                  <div className="bg-bg-base border border-border-subtle p-4 rounded-xl">
                      <h4 className="flex items-center text-red-400 font-medium mb-2"><AlertTriangle className="w-4 h-4 mr-2"/> New Findings</h4>
                      <p className="text-2xl font-bold">{comparison.newFindings.length}</p>
                      <p className="text-xs text-gray-400">Issues introduced between scans</p>
                  </div>
                  <div className="bg-bg-base border border-border-subtle p-4 rounded-xl">
                      <h4 className="flex items-center text-green-400 font-medium mb-2"><CheckCircle className="w-4 h-4 mr-2"/> Resolved Findings</h4>
                      <p className="text-2xl font-bold">{comparison.resolvedFindings.length}</p>
                      <p className="text-xs text-gray-400">Issues fixed or mitigated</p>
                  </div>
                  <div className="bg-bg-base border border-border-subtle p-4 rounded-xl">
                      <h4 className="flex items-center text-gray-300 font-medium mb-2"><Shield className="w-4 h-4 mr-2"/> Unchanged Findings</h4>
                      <p className="text-2xl font-bold">{comparison.unchangedFindings.length}</p>
                      <p className="text-xs text-gray-400">Lingering issues</p>
                  </div>
               </div>
               
               {/* Detail Lists */}
               <div className="space-y-4">
                  <h4 className="font-semibold text-gray-200 border-b border-border-subtle pb-2">New Findings Detailed</h4>
                  {comparison.newFindings.length === 0 ? <p className="text-sm text-gray-500">No new findings discovered.</p> : (
                      <ul className="space-y-2">
                          {comparison.newFindings.map(f => (
                              <li key={f.id} className="text-sm bg-red-500/10 border border-red-500/20 p-2 rounded-lg text-red-200">
                                  <span className="font-medium">[{f.severity}]</span> {sanitizeFindingTitle(f.title)} - {f.affectedUrl}
                              </li>
                          ))}
                      </ul>
                  )}
               </div>

               <div className="space-y-4">
                  <h4 className="font-semibold text-gray-200 border-b border-border-subtle pb-2">Resolved Findings Detailed</h4>
                  {comparison.resolvedFindings.length === 0 ? <p className="text-sm text-gray-500">No findings were resolved.</p> : (
                      <ul className="space-y-2">
                          {comparison.resolvedFindings.map(f => (
                              <li key={f.id} className="text-sm bg-green-500/10 border border-green-500/20 p-2 rounded-lg text-green-200">
                                  <span className="font-medium line-through">[{f.severity}]</span> {sanitizeFindingTitle(f.title)} - {f.affectedUrl}
                              </li>
                          ))}
                      </ul>
                  )}
               </div>
            </div>
        )}
      </div>
    </div>
  );
};

export default ScanComparison;
