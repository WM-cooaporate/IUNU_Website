import demoProperties from "./demoProperties";

const STORAGE_KEY = "iunuDemoProperties";
const UPDATE_EVENT = "iunu:demo-properties-updated";

export const getDemoProperties = () => {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored ? JSON.parse(stored) : demoProperties;
  } catch {
    return demoProperties;
  }
};

export const saveDemoProperties = (properties) => {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(properties));
  window.dispatchEvent(new Event(UPDATE_EVENT));
};

export const demoPropertiesUpdateEvent = UPDATE_EVENT;
