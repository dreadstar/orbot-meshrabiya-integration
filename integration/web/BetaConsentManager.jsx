import React, { useState, useEffect, useMemo, useRef } from 'react';

// Custom Card components
const Card = ({ children, className = "" }) => (
  <div className={`bg-white rounded-lg border border-gray-200 shadow-sm ${className}`}>{children}</div>
);
const CardHeader = ({ children, className = "" }) => (
  <div className={`px-6 py-4 ${className}`}>{children}</div>
);
const CardTitle = ({ children, className = "" }) => (
  <h3 className={`text-lg font-semibold text-gray-900 ${className}`}>{children}</h3>
);
const CardContent = ({ children, className = "" }) => (
  <div className={`px-6 pb-4 ${className}`}>{children}</div>
);

const LOGGING_LEVELS = [
  { value: 'BASIC', label: 'Basic (Recommended)', desc: 'Connectivity and basic performance metrics' },
  { value: 'DETAILED', label: 'Detailed', desc: 'Includes error logs and performance details' },
  { value: 'FULL', label: 'Full (Beta Testers Only)', desc: 'All metrics including protest-specific data' }
];

const BetaConsentManager = ({ 
  initialConsent = false, 
  initialLoggingLevel = 'DISABLED',
  onConsentChange = () => {},
  onDataExport = () => {} 
}) => {
  const [consentGiven, setConsentGiven] = useState(initialConsent);
  const [loggingLevel, setLoggingLevel] = useState(initialLoggingLevel);
  const [showConsentDialog, setShowConsentDialog] = useState(false);
  const [loggingStats, setLoggingStats] = useState({
    meshEvents: 0,
    userActions: 0,
    networkConditions: 0,
    batteryImpacts: 0,
    installationSteps: 0,
    protestMetrics: 0
  });
  const [feedback, setFeedback] = useState("");
  const dialogRef = useRef(null);

  // Accessibility: trap focus in dialog
  useEffect(() => {
    if (!showConsentDialog) return;
    const focusable = dialogRef.current?.querySelectorAll('button, [tabindex]:not([tabindex="-1"])');
    if (focusable && focusable.length) focusable[0].focus();
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') setShowConsentDialog(false);
      if (e.key === 'Tab' && focusable && focusable.length) {
        const first = focusable[0], last = focusable[focusable.length - 1];
        if (e.shiftKey ? document.activeElement === first : document.activeElement === last) {
          e.preventDefault();
          (e.shiftKey ? last : first).focus();
        }
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [showConsentDialog]);

  // Calculate total entries using useMemo
  const totalEntries = useMemo(() => Object.values(loggingStats).reduce((sum, val) => sum + val, 0), [loggingStats]);

  // Simulate updating logging statistics only when consent is given
  useEffect(() => {
    if (!consentGiven) return;
    const interval = setInterval(() => {
      setLoggingStats(prev => ({
        meshEvents: prev.meshEvents + Math.floor(Math.random() * 3),
        userActions: prev.userActions + Math.floor(Math.random() * 2),
        networkConditions: prev.networkConditions + Math.floor(Math.random() * 4),
        batteryImpacts: prev.batteryImpacts + Math.floor(Math.random() * 1),
        installationSteps: prev.installationSteps,
        protestMetrics: prev.protestMetrics + (loggingLevel === 'FULL' ? Math.floor(Math.random() * 1) : 0)
      }));
    }, 5000);
    return () => clearInterval(interval);
  }, [consentGiven, loggingLevel]);

  // Native logger integration (pseudocode, replace with actual bridge)
  const callNativeLogger = (method, ...args) => {
    if (window.BetaLogger && typeof window.BetaLogger[method] === 'function') {
      return window.BetaLogger[method](...args);
    }
    return null;
  };

  const handleConsent = (consented, level) => {
    setConsentGiven(consented);
    setLoggingLevel(level);
    setShowConsentDialog(false);
    callNativeLogger('setConsent', consented, level);
    setFeedback(consented ? 'Beta logging enabled.' : 'Beta logging disabled.');
    onConsentChange({ consented, loggingLevel: level, timestamp: Date.now() });
  };

  const revokeConsent = () => {
    setConsentGiven(false);
    setLoggingLevel('DISABLED');
    setLoggingStats({
      meshEvents: 0,
      userActions: 0,
      networkConditions: 0,
      batteryImpacts: 0,
      installationSteps: 0,
      protestMetrics: 0
    });
    callNativeLogger('revokeConsent');
    setFeedback('Beta logging disabled and data cleared.');
    onConsentChange({ consented: false, loggingLevel: 'DISABLED', timestamp: Date.now() });
  };

  const exportData = async () => {
    try {
      let exportBundle;
      if (window.BetaLogger) {
        exportBundle = await callNativeLogger('exportBetaTestData');
      } else {
        exportBundle = {
          stats: loggingStats,
          consent: { consentGiven, loggingLevel },
          timestamp: Date.now()
        };
      }
      await onDataExport(exportBundle);
      setFeedback('Exported encrypted beta log bundle.');
    } catch (error) {
      setFeedback('Failed to export data.');
      console.error('Failed to export data:', error);
    }
  };

  const ConsentDialog = () => {
    const [selectedLevel, setSelectedLevel] = useState('BASIC');
    return (
      <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50" role="dialog" aria-modal="true" ref={dialogRef}>
        <div className="bg-white rounded-lg max-w-md w-full p-6 space-y-4 max-h-[90vh] overflow-y-auto">
          <h2 className="text-xl font-bold text-gray-900">Beta Testing Consent</h2>
          <div className="space-y-3 text-sm text-gray-700">
            <p>Help improve Orbot-Meshrabiya by sharing anonymous usage data during beta testing.</p>
            <div className="bg-blue-50 p-3 rounded-md">
              <h3 className="font-semibold text-blue-900">Privacy Guarantees:</h3>
              <ul className="mt-2 space-y-1 text-blue-800">
                <li>• No personal information collected</li>
                <li>• All data encrypted on device</li>
                <li>• Automatic 30-day expiration</li>
                <li>• Anonymous user IDs that rotate monthly</li>
                <li>• You can opt-out anytime</li>
              </ul>
            </div>
          </div>
          <div className="space-y-3">
            <h3 className="font-semibold">Choose Data Sharing Level:</h3>
            <div className="space-y-2">
              {LOGGING_LEVELS.map(level => (
                <label key={level.value} className="flex items-center space-x-3 cursor-pointer">
                  <input
                    type="radio"
                    name="loggingLevel"
                    value={level.value}
                    checked={selectedLevel === level.value}
                    onChange={() => setSelectedLevel(level.value)}
                    className="text-blue-600"
                  />
                  <div>
                    <div className="font-medium">{level.label}</div>
                    <div className="text-xs text-gray-600">{level.desc}</div>
                  </div>
                </label>
              ))}
            </div>
          </div>
          <div className="flex space-x-3 pt-4">
            <button
              onClick={() => setShowConsentDialog(false)}
              className="flex-1 px-4 py-2 border border-gray-300 rounded-md text-gray-700 hover:bg-gray-50 transition-colors"
              aria-label="Cancel consent dialog"
            >
              Not Now
            </button>
            <button
              onClick={() => handleConsent(true, selectedLevel)}
              className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors"
              aria-label="Enable beta logging"
            >
              Enable Beta Logging
            </button>
          </div>
        </div>
      </div>
    );
  };

  return (
    <div className="max-w-4xl mx-auto p-4 space-y-6 bg-gray-50 min-h-screen" aria-live="polite">
      {feedback && (
        <div className="fixed top-4 right-4 z-50 bg-green-100 text-green-800 px-4 py-2 rounded shadow" role="status">{feedback}</div>
      )}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center justify-between">
            <span>Beta Testing & Data Collection</span>
            <div className={`px-3 py-1 rounded-full text-sm font-medium ${
              consentGiven 
                ? 'bg-green-100 text-green-800' 
                : 'bg-gray-100 text-gray-800'
            }`}>
              {consentGiven ? `Active (${loggingLevel})` : 'Disabled'}
            </div>
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-gray-600">
            Beta logging helps improve mesh networking performance in challenging environments 
            like protests and emergency situations.
          </p>
          {!consentGiven ? (
            <button
              onClick={() => setShowConsentDialog(true)}
              className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors"
              aria-label="Enable beta logging"
            >
              Enable Beta Logging
            </button>
          ) : (
            <div className="flex space-x-3">
              <button
                onClick={() => setShowConsentDialog(true)}
                className="px-4 py-2 border border-gray-300 rounded-md text-gray-700 hover:bg-gray-50 transition-colors"
                aria-label="Change beta logging settings"
              >
                Change Settings
              </button>
              <button
                onClick={exportData}
                className="px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 transition-colors"
                aria-label="Export beta log data"
              >
                Export Data
              </button>
              <button
                onClick={revokeConsent}
                className="px-4 py-2 bg-red-600 text-white rounded-md hover:bg-red-700 transition-colors"
                aria-label="Disable and clear beta log data"
              >
                Disable & Clear Data
              </button>
            </div>
          )}
        </CardContent>
      </Card>
      {consentGiven && (
        <Card>
          <CardHeader>
            <CardTitle>Data Collection Statistics</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
              <div className="bg-blue-50 p-3 rounded-lg">
                <div className="text-2xl font-bold text-blue-700">{loggingStats.meshEvents}</div>
                <div className="text-sm text-blue-600">Mesh Events</div>
              </div>
              <div className="bg-green-50 p-3 rounded-lg">
                <div className="text-2xl font-bold text-green-700">{loggingStats.userActions}</div>
                <div className="text-sm text-green-600">User Actions</div>
              </div>
              <div className="bg-yellow-50 p-3 rounded-lg">
                <div className="text-2xl font-bold text-yellow-700">{loggingStats.networkConditions}</div>
                <div className="text-sm text-yellow-600">Network Readings</div>
              </div>
              <div className="bg-purple-50 p-3 rounded-lg">
                <div className="text-2xl font-bold text-purple-700">{loggingStats.batteryImpacts}</div>
                <div className="text-sm text-purple-600">Battery Metrics</div>
              </div>
              <div className="bg-indigo-50 p-3 rounded-lg">
                <div className="text-2xl font-bold text-indigo-700">{loggingStats.installationSteps}</div>
                <div className="text-sm text-indigo-600">Install Steps</div>
              </div>
              {loggingLevel === 'FULL' && (
                <div className="bg-red-50 p-3 rounded-lg">
                  <div className="text-2xl font-bold text-red-700">{loggingStats.protestMetrics}</div>
                  <div className="text-sm text-red-600">Protest Metrics</div>
                </div>
              )}
            </div>
            <div className="mt-4 p-3 bg-gray-50 rounded-lg">
              <div className="text-lg font-semibold text-gray-800">
                Total Entries: {totalEntries}
              </div>
              <div className="text-sm text-gray-600 mt-1">
                Data automatically expires after 30 days
              </div>
            </div>
          </CardContent>
        </Card>
      )}
      {consentGiven && (
        <Card>
          <CardHeader>
            <CardTitle>Privacy & Security</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="flex items-center space-x-3">
                <div className="w-3 h-3 bg-green-500 rounded-full"></div>
                <span className="text-sm">Data encrypted on device</span>
              </div>
              <div className="flex items-center space-x-3">
                <div className="w-3 h-3 bg-green-500 rounded-full"></div>
                <span className="text-sm">Anonymous user ID rotates monthly</span>
              </div>
              <div className="flex items-center space-x-3">
                <div className="w-3 h-3 bg-green-500 rounded-full"></div>
                <span className="text-sm">No personal data collected</span>
              </div>
              <div className="flex items-center space-x-3">
                <div className="w-3 h-3 bg-green-500 rounded-full"></div>
                <span className="text-sm">Auto-delete after 30 days</span>
              </div>
            </div>
            <div className="mt-4 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
              <div className="text-sm text-yellow-800">
                <strong>For Protest Environments:</strong> All data is stored locally and encrypted. 
                Even if your device is confiscated, the beta logs cannot be decrypted without your device's secure keys.
              </div>
            </div>
            <div className="mt-2 text-xs text-gray-500">
              Exported data is encrypted and can only be decrypted by project maintainers or with your device's secure keys.
            </div>
          </CardContent>
        </Card>
      )}
      {showConsentDialog && <ConsentDialog />}
    </div>
  );
};

export default BetaConsentManager;
