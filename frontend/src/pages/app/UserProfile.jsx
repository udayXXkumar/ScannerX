import { useMemo, useState } from 'react';
import { AlertTriangle, Check, LockKeyhole, Trash2, UserRound } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { changePassword, deleteCurrentUser, updateProfile } from '../../api/authApi';

const UserProfile = () => {
  const navigate = useNavigate();
  const { user, updateUser, logoutUser } = useAuth();
  const [fullNameDraft, setFullNameDraft] = useState('');
  const [passwordForm, setPasswordForm] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });
  const [profileSaved, setProfileSaved] = useState(false);
  const [passwordSaved, setPasswordSaved] = useState(false);
  const [profileError, setProfileError] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [deleteError, setDeleteError] = useState('');

  const fullName = fullNameDraft || user?.fullName || '';

  const displayName = useMemo(
    () => fullName.trim() || user?.fullName || 'ScannerX User',
    [fullName, user?.fullName],
  );

  const handleProfileSave = (event) => {
    event.preventDefault();
    setProfileError('');
    updateProfile({ fullName: displayName })
      .then((updatedUser) => {
        updateUser({ fullName: updatedUser.fullName });
        setFullNameDraft('');
        setProfileSaved(true);
        window.setTimeout(() => setProfileSaved(false), 2200);
      })
      .catch((error) => {
        setProfileError(error.response?.data?.message || error.response?.data || 'Unable to update profile right now.');
      });
  };

  const handlePasswordSave = (event) => {
    event.preventDefault();

    if (!passwordForm.currentPassword.trim() || !passwordForm.newPassword.trim() || !passwordForm.confirmPassword.trim()) {
      setPasswordError('Fill all password fields.');
      return;
    }

    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      setPasswordError('New passwords do not match.');
      return;
    }

    setPasswordError('');
    changePassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword,
    })
      .then(() => {
        setPasswordForm({
          currentPassword: '',
          newPassword: '',
          confirmPassword: '',
        });
        setPasswordSaved(true);
        window.setTimeout(() => setPasswordSaved(false), 2200);
      })
      .catch((error) => {
        setPasswordError(error.response?.data?.message || error.response?.data || 'Unable to update password right now.');
      });
  };

  const handleDeleteAccount = () => {
    setDeleteError('');
    const confirmed = window.confirm('Delete this account and all owned workspace data?');

    if (!confirmed) {
      return;
    }

    deleteCurrentUser()
      .then(() => {
        logoutUser();
        navigate('/register');
      })
      .catch((error) => {
        setDeleteError(error.response?.data?.message || error.response?.data || 'Unable to delete account right now.');
      });
  };

  return (
    <div className="page-shell">
      <section className="page-card">
        <div className="flex flex-col gap-5 border-b border-white/8 pb-5 sm:flex-row sm:items-center">
          <div className="flex h-[72px] w-[72px] items-center justify-center rounded-full border border-white/10 bg-white/[0.04] text-white">
            <UserRound size={34} />
          </div>

          <div className="min-w-0">
            <h1 className="truncate text-[2rem] font-semibold text-white">{displayName}</h1>
            <p className="mt-1 text-base text-zinc-400">{user?.email || 'No email available'}</p>
          </div>
        </div>
      </section>

      <section className="page-card">
        <div className="mb-6">
          <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-zinc-500">Account</p>
          <h2 className="mt-3 text-[1.45rem] font-semibold text-white">Username</h2>
        </div>

        <form onSubmit={handleProfileSave} className="form-stack">
          <div className="grid gap-5 md:grid-cols-2">
            <ProfileField label="Username">
              <input
                type="text"
                value={fullName}
                onChange={(event) => setFullNameDraft(event.target.value)}
                className="modal-input"
                placeholder="Enter username"
              />
            </ProfileField>

            <ProfileField label="Email">
              <input
                type="email"
                value={user?.email || ''}
                readOnly
                className="modal-input opacity-75"
              />
            </ProfileField>
          </div>

          <div className="flex flex-col gap-4 border-t border-white/8 pt-5 sm:flex-row sm:items-center sm:justify-between">
            <StatusPill
              active={profileSaved}
              activeText="Saved"
              idleText={profileError || 'Ready'}
              danger={Boolean(profileError)}
            />
            <button type="submit" className="surface-button-primary min-w-[148px]">
              Save Changes
            </button>
          </div>
        </form>
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.35fr)_280px]">
        <section className="page-card">
          <div className="mb-6 flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-white/8 bg-white/[0.03] text-zinc-200">
              <LockKeyhole size={18} />
            </div>
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-zinc-500">Security</p>
              <h2 className="mt-1 text-[1.45rem] font-semibold text-white">Password</h2>
            </div>
          </div>

          <form onSubmit={handlePasswordSave} className="form-stack">
            <ProfileField label="Current Password">
              <input
                type="password"
                value={passwordForm.currentPassword}
                onChange={(event) =>
                  setPasswordForm((previous) => ({
                    ...previous,
                    currentPassword: event.target.value,
                  }))
                }
                className="modal-input"
                placeholder="Current password"
              />
            </ProfileField>

            <div className="grid gap-5 md:grid-cols-2">
              <ProfileField label="New Password">
                <input
                  type="password"
                  value={passwordForm.newPassword}
                  onChange={(event) =>
                    setPasswordForm((previous) => ({
                      ...previous,
                      newPassword: event.target.value,
                    }))
                  }
                  className="modal-input"
                  placeholder="New password"
                />
              </ProfileField>

              <ProfileField label="Confirm Password">
                <input
                  type="password"
                  value={passwordForm.confirmPassword}
                  onChange={(event) =>
                    setPasswordForm((previous) => ({
                      ...previous,
                      confirmPassword: event.target.value,
                    }))
                  }
                  className="modal-input"
                  placeholder="Confirm password"
                />
              </ProfileField>
            </div>

            <div className="flex flex-col gap-4 border-t border-white/8 pt-5 sm:flex-row sm:items-center sm:justify-between">
              <StatusPill
                active={passwordSaved}
                activeText="Updated"
                idleText={passwordError || 'Ready'}
                danger={Boolean(passwordError)}
              />
              <button type="submit" className="surface-button-primary min-w-[148px]">
                Update Password
              </button>
            </div>
          </form>
        </section>

        <section className="page-card h-full">
          <div className="mb-6 flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-rose-500/20 bg-rose-500/10 text-rose-300">
              <AlertTriangle size={18} />
            </div>
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-zinc-500">Danger Zone</p>
              <h2 className="mt-1 text-[1.35rem] font-semibold text-white">Delete Account</h2>
            </div>
          </div>

          <div className="flex flex-1 flex-col rounded-2xl border border-rose-500/16 bg-rose-500/[0.04] p-4">
            <p className="text-sm leading-6 text-zinc-300">
              Delete this account and remove all owned ScannerX data.
            </p>

            {deleteError ? (
              <div className="mt-4 rounded-xl border border-rose-500/20 bg-rose-500/10 px-3 py-2 text-sm text-rose-200">
                {deleteError}
              </div>
            ) : null}

            <button
              type="button"
              onClick={handleDeleteAccount}
              className="mt-auto inline-flex h-11 w-full items-center justify-center gap-2 rounded-xl border border-rose-500/24 bg-rose-500/12 px-4 text-sm font-semibold text-rose-200 transition-colors hover:bg-rose-500/18"
            >
              <Trash2 size={16} />
              Delete Account
            </button>
          </div>
        </section>
      </div>
    </div>
  );
};

const ProfileField = ({ label, children }) => (
  <div className="form-field">
    <label className="form-label">{label}</label>
    {children}
  </div>
);

const StatusPill = ({ active, activeText, idleText, danger = false }) => (
  <div
    className={`inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-medium ${
      danger
        ? 'border-rose-500/18 bg-rose-500/10 text-rose-200'
        : active
          ? 'border-emerald-400/18 bg-emerald-400/10 text-emerald-200'
          : 'border-white/8 bg-white/[0.03] text-zinc-400'
    }`}
  >
    {!danger ? <Check size={14} /> : null}
    <span>{active ? activeText : idleText}</span>
  </div>
);

export default UserProfile;
