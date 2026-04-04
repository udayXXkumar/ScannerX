import { Bell, Search } from 'lucide-react';

const Topbar = () => {
  return (
    <header className="h-16 shrink-0 bg-bg-panel border-b border-border-subtle flex items-center justify-between px-6">
      <div className="flex items-center bg-bg-base border border-border-subtle rounded-md px-3 py-1.5 w-96">
        <Search className="w-4 h-4 text-gray-400 mr-2" />
        <input 
          type="text" 
          placeholder="Search targets, scans, or findings..." 
          className="bg-transparent border-none outline-none w-full text-sm text-gray-200 placeholder-gray-500"
        />
      </div>
      
      <div className="flex items-center space-x-4">
        <button className="relative p-2 text-gray-400 hover:text-gray-100 transition-colors cursor-pointer">
          <Bell className="w-5 h-5" />
          <span className="absolute top-1.5 right-2 w-2 h-2 bg-primary rounded-full"></span>
        </button>
        <div className="flex items-center space-x-2 pl-4 border-l border-border-subtle cursor-pointer">
          <div className="w-8 h-8 bg-primary/20 text-primary rounded-full flex items-center justify-center font-medium">
            JD
          </div>
          <span className="text-sm font-medium">Jane Doe</span>
        </div>
      </div>
    </header>
  );
};
export default Topbar;
