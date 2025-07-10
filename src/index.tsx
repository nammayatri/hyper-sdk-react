/*
 * Copyright (c) Juspay Technologies.
 *
 * This source code is licensed under the AGPL 3.0 license found in the
 * LICENSE file in the root directory of this source tree.
 */

import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'hyper-sdk-react' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const HyperSdkReact = NativeModules["HyperSdkReact"]
  ? NativeModules["HyperSdkReact"]
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export { default as HyperFragmentView } from './HyperFragmentView';
export { default as PrestoView } from './PrestoView';

if (Platform.OS === 'android') {
  HyperSdkReact.updateBaseViewController = () => {};
  HyperSdkReact.updateMerchantViewHeight = (_tag: string, _height: number) => {
    console.log('UpdateMerchantViewHeight not available for android');
  };
}

if (Platform.OS === 'ios') {
  HyperSdkReact.processWithActivity = (data: string) => {
    HyperSdkReact.process(data);
  };
}

type HyperSdkReactType = {
  HyperEvent: string;
  preFetch(data: string): void;
  createHyperServices(key: string | null): void;
  initiate(data: string,key: string | null): void;
  process(data: string,key: string | null): void;
  processWithActivity(data: string,key: string | null): void;
  terminate(key: string | null): void;
  terminateAll(): void;
  onBackPressed(key: string | null): boolean;
  isNull(key: string | null): boolean;
  isInitialised(key: string | null): Promise<boolean>;
  updateBaseViewController(): void;
  openPaymentPage(data: string,key: string | null): void;
  updateMerchantViewHeight(tag: string, height: number): void;
  notifyAboutRegisterComponent(tag: string): void;
  JuspayHeader: string;
  JuspayHeaderAttached: string;
  JuspayFooter: string;
  JuspayFooterAttached: string;
};

export default HyperSdkReact as HyperSdkReactType;
